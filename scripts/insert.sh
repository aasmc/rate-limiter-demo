#!/bin/bash

echo -e "\n*** Submitting POST requests to Service ***\n"
printf "\n\n"
curl -X POST http://localhost:9091/items/create -H 'Content-Type: application/json' -d '{"itemName": "One","user": "Alex" }'
printf "\n\n"
curl -X POST http://localhost:9091/items/create -H 'Content-Type: application/json' -d '{"itemName": "Two","user": "Alex" }'
printf "\n\n"
curl -X POST http://localhost:9091/items/create -H 'Content-Type: application/json' -d '{"itemName": "Three","user": "Alex" }'
printf "\n\n"

curl -X POST http://localhost:9091/items/create -H 'Content-Type: application/json' -d '{"itemName": "Four","user": "Max" }'
printf "\n\n"

curl -X POST http://localhost:9091/items/create -H 'Content-Type: application/json' -d '{"itemName": "Five","user": "Max" }'
printf "\n\n"

curl -X POST http://localhost:9091/items/create -H 'Content-Type: application/json' -d '{"itemName": "Six","user": "Max" }'
printf "\n\n"

echo -e "\n*** Submitting requests to GET Items of Alex ***\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"

echo -e "\n*** Sleeping for 2 seconds ***"
sleep 2

echo -e "\n*** Submitting requests for Alex and Max ***\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Max
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Max
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Max
echo -e "\n*** Sleeping for 2 seconds ***"
sleep 2
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Max
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Max
printf "\n\n"
curl http://localhost:9091/items/Alex
printf "\n\n"
curl http://localhost:9091/items/Max
printf "\n\n"


