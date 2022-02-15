FROM docker.elastic.co/elasticsearch/elasticsearch:7.16.3

COPY .docker/elastic/qbm25-0.0.1.zip /plugins/qbm25-0.0.1.zip
COPY .docker/elastic/elasticsearch.yml /usr/share/elasticsearch/config/elasticsearch.yml

RUN elasticsearch-plugin install file:///plugins/qbm25-0.0.1.zip