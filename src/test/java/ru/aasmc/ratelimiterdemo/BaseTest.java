package ru.aasmc.ratelimiterdemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.aasmc.ratelimiterdemo.dto.UserItemsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ActiveProfiles("test")
@SqlGroup(
        {
                @Sql(
                        scripts = "classpath:insert-items.sql",
                        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
                ),
                @Sql(
                        scripts = "classpath:clear-items.sql",
                        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
                )
        }
)
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
