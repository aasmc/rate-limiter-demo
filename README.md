# RateLimiter Demo

Всем привет! Последние несколько месяцев я тружусь в одной из многочисленных команд проекта SberID. 
Мы разрабатываем личный кабинет для юридических лиц, которые хотят подключить в свои приложения вход по SberID.
Основная наша задача - сократить клиентский путь партнера с нескольких недель до нескольких минут. Несмотря на то, что 
мы лишь недавно (в августе) вышли в ПРОМ, определенных успехов удалось добиться. Сейчас наши партнеры могут подключить
SberID в течение нескольких дней, в то время как раньше эта процедура длилась недели. Естественно, это лишь начало пути, 
и нам приходится сталкиваться со множеством подводных камней, начиная с очевидных бюрократических сложностей, имеющихся
в любой крупной компании, заканчивая техническими ограничениями, возникающими из-за требований безопасности.

В одном из недавних релизов мы обнаружили недоработку, которая позволяла пользователю в течение короткого промежутка
времени несколько раз нажать на кнопку, при этом каждое такое нажатие сопровождалось тяжелым запросом на бэк. Естественно,
на UI мы это поправили в момент, а вот чтобы обезопасить бэк, пришлось помозговать. Глобальный рейт-лимит  присутствующий 
на каждой ручке нам не помог в этой ситуации, так как ограничивать требовалось количество запросов от конкретного партнера,
чтобы он не мог вызывать эндпоинт чаще чем 1 раз в 3 секунды (эти требования обусловлены спецификой самого эндпоинта).
Казалось бы, задача простая - подкрутить настройки лимитов в reverse-proxy, который стоит перед нашим сервисом (у нас
используется разработанный с Сбере шлюз безопасности на базе Nginx).
Но есть нюанс. Сетевая конфигурация нашего проекта очень сложная из-за требований безопасности, и перед нашим reverse-proxy стоит еще несколько
таких же. Тонкая настройка лимитов, конечно, возможна, но потребует определенных усилий со стороны команды DevOps. 
В итоге было принято решение ради одного бизнесового сценария провести доработку в коде силами своей команды.

Как вариант можно было бы использовать Bucket4j в связке с Redis - любые in-memory
решения не подходят, так как приложение развернуто в нескольких экземплярах. Но чтобы затащить Redis в проект опять потребовались бы
усилия команды DevOps + согласование на всех уровнях, так как это расширение инфраструктуры. А решение нужно здесь и сейчас. 


## Решение
Мы решили (~~изобрести велосипед~~) написать простой распределенный рейт-лимитер для отдельной ручки на основе уже
имеющихся компонентов, а именно СУБД PostgreSQL. Рассмотрели несколько вариантов, о каждом из которых расскажу подробнее ниже.
Все примеры кода доступны в репозитории [GitHub](https://github.com/aasmc/rate-limiter-demo). 

### Вариант с двумя походами в БД.
Суть этого решения проста - создаем в БД таблицу, в которой храним данные о пользователе, и времени последнего запроса.
На столбец с идентификатором пользователя навешиваем уникальный индекс, также в конфигах определяем интервал, в течение
которого пользователю разрешено выполнять 1 запрос, в нашем случае это 3 секунды. 

```sql
create table user_ratelimiter(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY not null,
    user_name text not null,
    request_dt timestamp with time zone not null
);

create unique index i_user_ratelimiter_user_name on user_ratelimiter(user_name);
```

В момент вызова эндпоинта осуществляем проверку по следующему алгоритму:
1. Пытаемся получить запись о рейт-лимите пользователя с помощью `select for update nowait`. Этот запрос
   будет успешен только в том случае, если запись в БД есть, и никакой другой поток ее ранее не заблокировал.
   Если запись уже заблокирована другим потоком, то сразу выбросится исключение, которое 
   в Spring Boot транслируется в `UncategorizedSQLException`. 
2. Если поймали `UncategorizedSQLException`, значит рейт-лимит не пройден и запрос не разрешен.
3. Иначе если получили запись из БД, смотрим на время последнего запроса (колонка `request_dt`). 
4. Если оно меньше, чем `now() - разрешенный интервал`, значит рейт-лимит пройден и запрос разрешен. Устанавливаем `now()` в `request_dt` и обовляем запись в БД.
5. Иначе - рейт-лимит не пройден и запрос не разрешен.
6. Если записи нет, то пытаемся создать новую. При этом только у одного потока получится это сделать,
   так как на столбец с идентификатором пользователя навешен уникальный индекс. 
7. Если запись создана в БД успешно - рейт-лимитер пройден и запрос разрешен.
8. Иначе - рейт-лимитер не пройден.


```java
@Repository
public interface UserRateLimiterRepository extends CrudRepository<UserRateLimiter, Long> {

    @Query("select * from user_ratelimiter where user_name = :userName for update nowait")
    Optional<UserRateLimiter> findByUserIdForUpdateNoWait(String userName);

}
```

```java
    @Transactional
    public void permitRequestOrThrow(String userName) {
        try {
            permitRequestOrThrowInternal(userName);
        } catch (UncategorizedSQLException ex) {
            // thrown when select for update no wait doesn't allow to proceed
            log.error(ex.getMessage());
            registerMetric(userName, STATUS_ERROR);
            throw new ServiceException(HttpStatus.TOO_MANY_REQUESTS,  "Cannot allow request. Error " + ex.getMessage());
        }
    }

    private void permitRequestOrThrowInternal(String userName) {
        userRateLimiterRepository.findByUserIdForUpdateNoWait(userName)
                .ifPresentOrElse(
                        rl -> {
                            Instant now = Instant.now();
                            if (allowedToProceed(rl, now)) {
                                rl.setRequestTime(now);
                                userRateLimiterRepository.save(rl);
                                log.info("Request permitted");
                                registerMetric(userName, STATUS_SUCCESS);
                            } else {
                                log.error("Request not permitted");
                                registerMetric(userName, STATUS_ERROR);
                                throw new ServiceException(HttpStatus.TOO_MANY_REQUESTS, "Cannot allow request.");
                            }
                        },
                        () -> {
                            UserRateLimiter rl = new UserRateLimiter(null, userName, Instant.now());
                            try {
                                log.info("Trying to permit request.");
                                userRateLimiterRepository.save(rl);
                                registerMetric(userName, STATUS_SUCCESS);
                            } catch (DbActionExecutionException ex) {
                                log.error("Failed to permit request. Exception = {}", ex.getMessage());
                                registerMetric(userName, STATUS_ERROR);
                                throw new ServiceException(HttpStatus.TOO_MANY_REQUESTS, "Cannot allow request. Error " + ex.getMessage());
                            }
                        }
                );
    }

    private boolean allowedToProceed(UserRateLimiter rl, Instant now) {
        //       request_dt   (now - allowedRequestPeriod)    now
        // time: ___|_________________|_____________________|_____
        return now.minus(allowedRequestPeriod).isAfter(rl.getRequestTime());
    }
```

Решение на первый взгляд неоптимальное, но рабочее. Учитывая нашу нагрузку, оно нам подходит. 
И все-таки попробуем его оптимизировать.

### Вариант с одним запросом (tstzrange) 
Попытка номер 1. Нам потребуется задействовать тип данных PostgreSQL `TSTZRANGE` - интервал `timestamp with timezone` (у нас обязательно 
использование `timstamp with timezone` на проекте).

```sql
create extension btree_gist;

create table user_ratelimiter(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY not null,
    user_name text not null,
    limiter_range tstzrange not null,
    create_dt timestamp with time zone not null,
    constraint user_limiter_exclusion_constraint
        exclude using gist (user_name with =, limiter_range with &&)
);

create index i_user_ratelimiter_create_dt on user_ratelimiter(create_dt);
```
С помощью расширения `btree_gist` на столбец `limiter_range` навешиваем исключающее ограничение, которое при попытке вставить запись будет проверять, 
что для пользователя `user_name` в БД нет записи с пересекающимся интервалом `limiter_range`. О том, зачем нам
индекс на дату `create_dt` расскажу чуть ниже. 

Алгоритм проверки при этом меняется:
1. Пытаемся вставить новую запись с интервалом `[now(), now().plus(allowedRequestPeriod))`.
2. Если запись создана успешно, значит в БД нет пересекающихся интервалов и запрос разрешен.
3. Иначе интервал текущего запроса пересекается с каким-то из недавно совершенных, и запрос неразрешен. В этом
   случае выбросится исключение, которое будет транслировано Spring Boot в `DataIntegrityViolationException`.

```java
public interface UserRateLimiterRepository extends CrudRepository<UserRateLimiter, Long> {


    @Modifying
    @Query("insert into user_ratelimiter(user_name, limiter_range, create_dt) " +
            "values(:userName, tstzrange(:range), :created)")
    int insert(String userName, Instant created, String range);

    @Modifying
    @Query("delete from user_ratelimiter where create_dt < :before")
    void deleteBefore(Instant before);

}
```

```java
    public void permitRequestOrThrow(String userName) {
        try {
            log.info("Trying to acquire permit on userRateLimiter for user {}", userName);
            TimestampRange range = new TimestampRange(OffsetDateTime.now(), OffsetDateTime.now().plus(allowedRequestPeriod));
            userRateLimiterRepository.insert(userName, Instant.now(), range.toString());
            log.info("Successfully acquired permit on userRateLimiter for user {}", userName);
            registerMetric(userName, STATUS_SUCCESS);
        } catch (DataIntegrityViolationException ex) {
            log.error(ex.getMessage());
            registerMetric(userName, STATUS_ERROR);
            throw new ServiceException(HttpStatus.TOO_MANY_REQUESTS, "Cannot allow request. Error " + ex.getMessage());
        }
    }
```

Казалось бы, один поход в БД лучше, чем два. Но, как обычно, есть нюансы, о которых речь пойдет ниже. А сначала попытаемся оптимизировать 
первый вариант так, чтобы и там был только один поход в БД.

### Оптимизируем вариант с двумя походами в БД
Попытка номер 2. В первом рассмотренном нами варианте есть изъян - 2 запроса в БД. Избавиться от второго запроса нам позволит
тип данных PostgreSQL `interval`, который можно использовать для прибавления к `timestamp with time zone`, и 
получить на выходе `timestamp with time zone`. Подробнее об оберациях с датами и временем в PostgreSQL хорошо описано 
документации (https://www.postgresql.org/docs/current/functions-datetime.html). Java `Duration` можно сконвертировать в `interval`
в нативном sql-запросе с помощью приведения типов: `duration::interval`. Также воспользуемся механизмом
`insert on conflict do update`. 

Алгоритм запроса получится такой:
1. Пытаемся вставить запись в БД
2. В случае конфликта по полю `user_name` (оно уникальное), обновляем время запроса `request_dt`, но только
   если запись удовлетворяет условию: `timestamp нового запроса - разрешенный интервал запроса > timestamp запроса в записи` 
3. Если условие удовлетворяет - вернуть запись
4. Если нет - вернуть null

Сам запрос выглядит следующим образом:

```java
    @Query("""
            insert into user_ratelimiter(user_name, request_dt)
            values (:userName, :now)
            on conflict(user_name) do update
            set request_dt = excluded.request_dt
            where excluded.request_dt - :allowedRequestPeriod::interval > user_ratelimiter.request_dt
            returning *;
            """)
    Optional<UserRateLimiter> acquireToken(String userName, Duration allowedRequestPeriod, Instant now);
```

Метод сервиса также становится значительно проще:

```java
    public void acquire(String userName) {
        userRateLimiterRepository.acquireToken(userName, allowedRequestPeriod, Instant.now())
                .orElseThrow(() -> {
                    registerMetric(userName, STATUS_ERROR);
                    return new ServiceException(HttpStatus.TOO_MANY_REQUESTS, "Cannot allow request");
                });
        registerMetric(userName, STATUS_SUCCESS);
    }
```

## Тестирование
Первым делом мы протестировали все реализации с помощью нехитрого сценария - одновременно запускаем 
10 конкурентных потоков и ожидаем, что только 1 из них выполнится успешно. Тест был зеленым во всех
реализациях.

```java
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class BaseTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void testConcurrent() throws Exception {
        int concurrentRequests = 10;
        String userName = "Alex";
        String url = "/items/" + userName;
        List<Callable<FutureResult>> callables = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (int i = 0; i < concurrentRequests; i++) {
            Callable<FutureResult> callable = () -> {
                FutureResult futureResult = new FutureResult();
                MvcResult mvcResult = mvc.perform(get(url))
                        .andReturn();
                MockHttpServletResponse response = mvcResult.getResponse();
                if (response.getStatus() == 200) {
                    futureResult.setResponse(mapper.readValue(response.getContentAsString(), UserItemsResponse.class));
                } else {
                    futureResult.setError(response.getContentAsString());
                }
                return futureResult;
            };
            callables.add(callable);
        }

        List<Future<FutureResult>> futures = executor.invokeAll(callables);
        List<FutureResult> results = new ArrayList<>();
        for (Future<FutureResult> futureResult : futures) {
            results.add(futureResult.get());
        }
        List<FutureResult> successes = results.stream().filter(FutureResult::isSuccess).toList();
        List<FutureResult> errors = results.stream().filter(FutureResult::isError).toList();

        assertThat(successes).hasSize(1);
        successes.forEach(System.out::println);
        errors.forEach(System.out::println);
        executor.shutdown();
    }

    static class FutureResult {
        private UserItemsResponse response;
        private String error;

        public boolean isSuccess() {
            return response != null && error == null;
        }

        public boolean isError() {
            return error != null && response == null;
        }

        public UserItemsResponse getResponse() {
            return response;
        }

        public void setResponse(UserItemsResponse response) {
            this.response = response;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        @Override
        public String toString() {
            return "FutureResult{" +
                    "response=" + response +
                    ", error='" + error + '\'' +
                    '}';
        }
    }

}
```

Однако при проверке с большей нагрузкой выяснилось, что второй вариант с одним запросом к БД и `TSTZRANGE` не справляется и 
приводит к блокировкам строк и дэдлокам вплоть до того, что в какой-то момент сервис становится полностью недоступным. Дело том, что
проверка исключающего ограничения на пересечение интервалов - тяжелая операция, требующая блокировок. 
Конкурентные потоки пытаются вставить новую запись и запускают проверку, при этом так получается, что порядок захвата 
блокировки индексных страниц непредсказуем и может приводить к циклическим зависимостям или дэдлокам.
Другими минусами второго подхода является - привязка к типу данных PostgreSQL `TSTZRANGE`, а также
необходимость периодически запускать джобу по очистке данных таблицы `user_ratelimiter`, так как на каждый
успешный запрос в базе создается запись. Шедулер при этом должен учитывать, что развернуто несколько инстансов
приложения (мы в таких случаях используем библиотеку shedlock). И как раз для этого шедулера был создан индекс на
`create_dt`, чтобы эффективнее удалять все записи, у которых время создания меньше некоего настраиваемого порога.
В демо проекте приведена реализация шедулера.

При этом в первом варианте таких проблем нет, так как проверка `select for update nowait` не заставляет поток ждать освобождения блокировки,
а проверка уникальности поля `user_name` будет происходить лишь раз при первой вставке записи в БД.

В третьем варианте все немного сложнее. Конкурентные потоки будут всегда пытаться сделать `insert`, один из них возьмет rol-level lock
и попытается либо вставить, либо обновить запись, если есть конфликт по `user_name`. Остальные будут дожидаться 
своей очереди. Фактически работа с одной строкой сериализуется между потоками. Тестирование с помощью gatling
показало, что, несмотря на такую сериализацию, время выполнения метода `acquire` - прохождение рейт-лимита - в среднем
примерно соответсвтует времени выполнения того же метода с `select for update nowait`. Однако график получился чуть более рваный.

## insert on conflict do update
![Single Query RL.png](art/Single%20Query%20RL.png)

## select for update nowait
![Select For Update RL.png](art/Select%20For%20Update%20RL.png)


## Демо
В тестовом проекте подготовлено небольшое Spring Boot приложение, выставляющее наружу 2 эндпоинта:
1. `POST /items/create` Создание сущности пользователем
2. `GET /items/{user}` Получение списка сущностей, созданных пользователем

Получение списка защищено рейт-лимитером, который разрешает только один запрос от пользователя в 2 секунды.

В `docker-compose.yml` подготовлены инфраструктурные компонены для мониторинга, и старта приложения:
1. Postgres
2. Prometheus
3. Grafana
4. Loki
5. Tempo

Также подготовлено описание контейнеров для двух инстансов приложения.

Само приложение можно собрать из `Dockerfile` в корне проекта:
```shell
./gradlew clean build
docker build -t ratelimiter-demo .
```

После запуска контейнеров:
```shell
docker compose up -d
```
Можно запустить подготовленную симуляцию на Gatling. 
```shell
./gradlew gatlingRun
```

В симуляции тестируется, что за 10 минут, один пользователь может отправить не более
300 GET-запросов (1 запрос в 2 секунды). Нагрузка неравномерная:
1. Начинаем с 5 запросов в секунду в течение 3 минут
2. Увеличиваем нагрузку до 50 запросов в секунду в течение 3 минут
3. Поддерживаем нагрузку в 100 запросов в секунду в течение 4 минут

Сценариев запуска нагрузки два - по одному на инстанс сервиса. Они выполняются параллельно.
Независимо от количества инстансов, общее количество успешных запросов пользователя на панели
мониторинга после отработки скрипта должно быть не более 300.

На панели мониторинга в Grafane (доступна по http://localhost:3000, вкладка Dashboards/RateLimiterService/Monitoring RateLimiter)
настроены визуализации метрик:
1. максимальное время прохождения рейт-лимита (с ошибкой и без)
2. 99 перцентиль времени выполнения запросов
3. среднее время прохождения рейт-лимита (с ошибкой и без)
4. среднее время выполнения запросов (график по времени)
5. процент успешных запросов
6. среднее время выполнения запросов (в моменте)
7. общее количество успешных вызовов GET-запроса
8. общее количество успешых вызовов POST-запроса
9. количество успешных проходов рейт-лимита
10. количество ошибок при проходе рейт-лимита
11. процент успешных проходов рейт-лимита
12. процент ошибок при проходе рейт-лимита

Запустив сценарии для сборки из ветки master (реализация первого варианта) или single-query-rate-limiter (реализация второго варианта), видно, что поведение
сервиса ожидаемо. Чуть меньше 300 запросов былы успешны. 

В то время как запуск симуляции для сборки из ветки tstzrange приводит к тому, что сервис через какое-то время перестает отвечать,
а количество успешных вызовов гораздо ниже ожидаемого. При этом в логах postgres такие записи:

```text
2024-12-09 12:32:03 2024-12-09 09:32:03.282 UTC [134] ERROR:  deadlock detected
2024-12-09 12:32:03 2024-12-09 09:32:03.282 UTC [134] DETAIL:  Process 134 waits for ShareLock on transaction 1189; blocked by process 135.
2024-12-09 12:32:03     Process 135 waits for ShareLock on transaction 1190; blocked by process 134.
2024-12-09 12:32:03     Process 134: insert into user_ratelimiter(user_name, limiter_range, create_dt) values($1, tstzrange($2), $3)
2024-12-09 12:32:03     Process 135: insert into user_ratelimiter(user_name, limiter_range, create_dt) values($1, tstzrange($2), $3)
```

## Итог
Не все решения, которые выглядят оптимальными на первый взгляд, на самом деле таковы. Стоит тщательно
тестировать реализацию в том числе под нагрузкой, чтобы убедиться, что вы не упустили какие-то скрытые детали.
Мы остановились на первом варианте, несмотря на то, что он в худшем случае требует двух походов в БД. 