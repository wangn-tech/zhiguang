FROM docker.elastic.co/elasticsearch/elasticsearch:9.2.1

# IK 是中文分词插件，必须与基础 Elasticsearch 镜像使用完全相同的版本。
RUN bin/elasticsearch-plugin install --batch \
    https://get.infini.cloud/elasticsearch/analysis-ik/9.2.1
