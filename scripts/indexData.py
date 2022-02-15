from elasticsearch import Elasticsearch
from elasticsearch.helpers import streaming_bulk
import sys
import json
import pandas as pd

es = Elasticsearch(['localhost'], port=9200, http_auth=("elastic", "changeme"))


# https://stackoverflow.com/questions/49726229/how-to-export-pandas-data-to-elasticsearch/49982341
def document_stream(indexToAdd):
    df = pd.read_csv("../data/" + indexToAdd + ".csv")
    for record in df.to_dict(orient="records"):
        yield {
            "_index": indexToAdd,
            "_source": json.dumps(record, default=int)
        }


def index(indexToAdd='reddit_sentences_processed'):

    if indexToAdd == 'reddit_sentences_processed':
        mapping = {
            "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0
            },
            "mappings": {
                "properties": {
                    "post_index": {
                        "type": "integer"
                    },
                    "paragraph_index": {
                        "type": "integer"
                    },
                    "url": {
                        "type": "text"
                    },
                    "subreddit": {
                        "type": "keyword"
                    },
                    "num_comments": {
                        "type": "integer"
                    },
                    "score": {
                        "type": "integer"
                    },
                    "title": {
                        "type": "text"
                    },
                    "sentence": {
                        "type": "text"
                    },
                    "units": {
                        "type": "keyword"
                    },
                    "values": {
                        "type": "keyword"
                    },
                    "surfaces": {
                        "type": "keyword"
                    },
                    "date": {
                        "type": "date",
                        "format": "date_optional_time",
                        "null_value": "2020-12-31T18:38:11Z",
                        "ignore_malformed": "true"
                    }
                }
            }
        }
    elif "reddit_paragraphs_processed":
        mapping = {
            "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0
            },
            "mappings": {
                "properties": {
                    "article_index": {
                        "type": "integer"
                    },
                    "paragraph_index": {
                        "type": "integer"
                    },
                    "url": {
                        "type": "text"
                    },
                    "subreddit": {
                        "type": "keyword"
                    },
                    "num_comments": {
                        "type": "integer"
                    },
                    "score": {
                        "type": "integer"
                    },
                    "title": {
                        "type": "text"
                    },
                    "paragraph": {
                        "type": "text"
                    },
                    "date": {
                        "type": "date",
                        "format": "date_optional_time",
                        "null_value": "2020-12-31T18:38:11Z",
                        "ignore_malformed": "true"
                    }
                }
            }
        }

    response = es.indices.delete(index=indexToAdd, ignore=[400, 404])
    response = es.indices.create(index=indexToAdd, body=mapping)

    stream = document_stream(indexToAdd)
    for ok, response in streaming_bulk(es, actions=stream):
        if not ok:
            print(response)

    response = es.indices.flush(index=indexToAdd)
    response = es.indices.refresh(index=indexToAdd)


indexToAdd = sys.argv[1]
index(indexToAdd)
