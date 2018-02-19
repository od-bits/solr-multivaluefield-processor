# solr-multivaluefield-processor
Solr update request processor for creating new multivalue field by parsing some text field

## Build processor
* clone repo `git clone https://github.com/od-bits/solr-multivaluefield-processor.git`
* go to folder `cd solr-multivaluefield-processor`
* build with Maven `mvn package`

## Test processor with docker
* run Solr on Docker
`docker run --name multivalue -it -p8983:8983 solr:6.5`
* copy created jar to contrib
`docker cp target/solr-multivaluefield-processor-*.jar multivalue:/opt/solr/contrib/`
* create test core
`docker exec -it multivalue bin/solr create_core -c min -d basic_configs`
* override solrconfig.xml with one with custom updateRequestProcessor
`docker cp src/test/resources/solrconfig.xml multivalue:/opt/solr/server/solr/multivalue/conf/`
* reload core
`curl 'localhost:8983/solr/admin/cores?action=RELOAD&core=multivalue'`
* index test doc
`curl -XPOST 'localhost:8983/solr/multivalue/update?commit=true&wt=json&indent=true' -d '[{"id": 1, "tags_txt": "solr facet"}]'`
* query docs to see that tags_ss is added
`curl 'localhost:8983/solr/min/select?wt=json&indent=true&q=*:*'`

Related blog post: http://www.od-bits.com/2018/02/solr-docvalues-on-analysed-field.html
