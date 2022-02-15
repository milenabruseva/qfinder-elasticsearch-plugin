# qfinder-elasticsearch-plugin

## Overview
This repository contains an implementation of a plugin for Elasticsearch, based on the paper 
"Why Complicate Things? Non-neural Quantity-centric Retrieval". 

The current implementation uses a scoring script to rescore the documents returned by Elasticsearch using the QBM25 algorithm.
QBM25 is a numerically aware enhanced algorithm built on top of BM25 that can query large amount of unstructured text with numerical information,
with different conditions. It can retrieve sentences that contain the exact numerical value and unit in regard to 
specific keywords, and also values greater than, less than or in a specified range.

The current plugin has been built for Elasticsearch version: **7.16.3**.

## How to run

#### To build
- Build container to compile plugin: ``docker build . -t esplugin-java``
- Run it: ``docker run --rm -it -v ${PWD}:/plugin esplugin-java bash`` (note: this also opens a shell inside the container)
- Run inside container: ``gradle build``

#### To clean
- Inside the docker container ``esplugin-java`` run ``gradle clean``

#### To run
- Copy the .zip file from build/distributions/ to .docker/elastic/
- In the root directory of the project run ``docker-compose build`` and ``docker-compose up``

#### To verify
- Go to ``http://localhost:9200/_cat/plugins?v`` to see the list of loaded plugins
- Add data to index: in folder scripts run `indexData.py reddit_sentences_processed`, where `reddit_sentences_processed` can be replaced with your dataset. Note that some modification could be necessary if you require different index mappings.
- You can find a jupyter notebook in the scripts folder which is used for processing a sample dataset, containing posts from the financial subreddits (source: https://www.kaggle.com/yorkehead/stock-market-subreddits).
- In order to make use of our score normalization you will need to make two queries to Elasticsearch. One without the score script to get the maximum score returned by the internal BM25 algorithm and one using the score script to get the QBM25 score of each document and rank them accordingly.
- Make a query to `localhost:9200/<indexName>/_search` with a JSON body for the request, for example:
```
curl --location --request GET 'localhost:9200/reddit_sentences_processed/_search' \
--header 'Content-Type: application/json' \
--data-raw '{
      "query": {
        "bool": {
            "must": [
                {"multi_match": {
                    "query": "gme price",
                    "fields": ["sentence"]
                }}
            ]
        }
    }
}'
```

- Then use the `["hits"]["max_score"]` value returned to make the query using the script scoring module and configure the parameters. For example:
```
curl --location --request GET 'localhost:9200/reddit_sentences_processed/_search' \
--header 'Content-Type: application/json' \
--data-raw '{
  "query": {
    "function_score": {
      "query": {
        "bool": {
            "must": [
                {"multi_match": {
                    "query": "gme price",
                    "fields": ["sentence"]
                }}
            ]
        }
    },
      "functions": [
        {
          "script_score": {
            "script": {
                "source": "qbm25",
                "lang" : "expert_scripts",
                "params": {
                    "handler": ">",
                    "unit": "dollar",
                    "amount": "100",
                    "amount2": "0", 
                    "weight": "2",
                    "max_score": 11.109755
                }
            }
          }
        }
      ],
      "boost_mode": "replace"
    }
  }
}'
```
- This can be done easily using curl or Postman(recommended).
- You can replace the "query" part of the "function_score" according to your needs.

## How to use
Using this plugin requires the use of an [Elasticsearch Function Score Query](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-function-score-query.html).

Within the "function_score" block, the "query" follows the Elasticsearch query DSL rules.

Within the "script" part of the request body is where we define the parameters for our script (all are required):
- `"source": "qbm25"` - the id of the script.
- `"lang" : "expert_scripts"` - the language of the script. In this case it is `"expert_scripts"`, as it is a custom script, written as part of a plugin.
- `"handler"` - the numerical condition to consider for the retrieval, the choices are "equal" (=), "greater" (>), "smaller" (<), "range" (<<).
- `"unit"` - unit or combination of units, separated by `,`.
- `"amount"` - the number to look for in the standard case. Or the lower bound in case of a range search.
- `"amount2"` - in case of a range, a second number specifying the upper bound. This parameter is otherwise ignored.
- `"weight"` - the weighting for the numeric part of the algorithm.
- `"max_score"` - the maximum score returned by the same query without a script. This is used to normalize the scores.

The `"boost_mode"` we need to use is `replace`, as the script modifies the returned BM25 score of each document internally.

## How to install
- Copy the `.zip` file to the server running Elasticsearch where you want to install it.
- If updating, the plugin must first be removed:
```sudo /usr/share/elasticsearch/bin/elasticsearch-plugin remove qbm25```
- Install the plugin using the built-in installer. Replace `file_path` with the location where you copied the file. 
```sudo /usr/share/elasticsearch/bin/elasticsearch-plugin install file:///<file_path>/qbm25-0.0.1.zip```
- Finally, restart Elasticsearch on the node:
```sudo systemctl restart elasticsearch```
-  Note that when running in a cluster with multiple nodes, the plugin must be installed on each node separately.

## Resources
Initially implemented following: 
- <https://www.viget.com/articles/lets-write-a-dang-elasticsearch-plugin/>
- <https://www.elastic.co/guide/en/elasticsearch/plugins/current/plugin-authors.html>
- <https://github.com/elastic/elasticsearch/blob/master/plugins/examples/script-expert-scoring/src/main/java/org/elasticsearch/example/expertscript/ExpertScriptPlugin.java>