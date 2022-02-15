import re
import requests


url = "localhost:9200/reddit_sentences_processed/_search"
params = {
    "aggs": {
        "units": {
            "terms": {"field": "units", "size": 10000000}
        }
    }
}

response = requests.get(url=url, params=params)
body = response.json()
aggs = body["aggregations"]["units"]["buckets"]
unique_units = set(re.findall(r"'(.*?)'", str(aggs), re.DOTALL))

text_file = open("units_catalogue.txt", "w")
text_file.write(str(unique_units))
text_file.close()

