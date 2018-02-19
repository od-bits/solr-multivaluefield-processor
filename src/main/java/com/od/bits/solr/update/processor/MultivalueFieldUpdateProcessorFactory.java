package com.od.bits.solr.update.processor;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.FieldType;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/***
 * 
* <pre class="prettyprint">
*   &lt;updateRequestProcessorChain name="multivalue"&gt;
*     &lt;processor class="com.od.bits.solr.update.processor.MultivalueFieldUpdateProcessorFactory"&gt;
*       &lt;str name="sourceField"&gt;tags_txt&lt;/str&gt;
*       &lt;str name="destField"&gt;tags_ss&lt;/str&gt;
*       &lt;str name="analysisType"&gt;text_ws&lt;/str&gt;
*     &lt;/processor&gt;
*     ...
*   &lt;/updateRequestProcessorChain&gt;
* </pre>
**/

/**
 * <p>
 * Update request processor that takes value/s from some text field (sourceField),  applies indexing analysis chain
 * of defined field type (analysisType) and adds values as some multivalue field (destField).
 * </p>
 * <p>Sample configuration: </p>
 * <pre class="prettyprint">
 *   &lt;updateRequestProcessorChain name="multivalue"&gt;
 *     &lt;processor class="com.od.bits.solr.update.processor.MultivalueFieldUpdateProcessorFactory"&gt;
 *       &lt;str name="sourceField"&gt;tags_txt&lt;/str&gt;
 *       &lt;str name="destField"&gt;tags_ss&lt;/str&gt;
 *       &lt;str name="analysisType"&gt;text_ws&lt;/str&gt;
 *     &lt;/processor&gt;
 *     ...
 *   &lt;/updateRequestProcessorChain&gt;
 * </pre>
 * 
 * @author emir
 *
 */
public class MultivalueFieldUpdateProcessorFactory extends UpdateRequestProcessorFactory implements SolrCoreAware {
	private static final Logger LOG = LoggerFactory.getLogger(MultivalueFieldUpdateProcessor.class);
	
	private static final String SOURCE_FIELD = "sourceField";
	private static final String DESTINATION_FIELD = "destField";
	private static final String ANALYSIS_TYPE = "analysisType";

	private String sourceFieldName;

	private String destFieldName;

	private String analysisTypeName;

	private Analyzer multivalueAnalyzer;
	
	@Override
	public UpdateRequestProcessor getInstance(SolrQueryRequest req, SolrQueryResponse rsp, UpdateRequestProcessor next) {
		return new MultivalueFieldUpdateProcessor(next);
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	public void init(NamedList args) {
		LOG.error("Initializing with params {}", args);
		this.sourceFieldName = getMandatoryParam(args, SOURCE_FIELD);
		this.destFieldName = getMandatoryParam(args, DESTINATION_FIELD);
		this.analysisTypeName = getMandatoryParam(args, ANALYSIS_TYPE);
		LOG.error("Initialized {}", toString());
		super.init(args);
	}
	
	@SuppressWarnings("rawtypes")
	private String getMandatoryParam(NamedList args, String param) {
		Object source = args.get(param);
		if (source != null) {
			return source.toString();
		}
		throw new SolrException(ErrorCode.SERVER_ERROR, String.format("Missing mandatory parametere '%s'", param));
	}
	
	@Override
	public void inform(SolrCore core) {
		FieldType analysisType = core.getLatestSchema().getFieldTypeByName(this.analysisTypeName);
		if (analysisType != null) {
			this.multivalueAnalyzer = analysisType.getIndexAnalyzer(); 
		} else {
			throw new SolrException(ErrorCode.SERVER_ERROR, String.format("Field type '%s' not defined", this.analysisTypeName));
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s: {%s: '%s', %s: '%s', %s: '%s'}",
				MultivalueFieldUpdateProcessorFactory.class.getSimpleName(),
				SOURCE_FIELD, this.sourceFieldName,
				DESTINATION_FIELD, this.destFieldName,
				ANALYSIS_TYPE, this.analysisTypeName);
	}
	
	private class MultivalueFieldUpdateProcessor extends UpdateRequestProcessor {

		public MultivalueFieldUpdateProcessor(UpdateRequestProcessor next) {
			super(next);
		}
		
		@Override
		public void processAdd(AddUpdateCommand cmd) throws IOException {
			SolrInputDocument doc = cmd.getSolrInputDocument();
			SolrInputField inputField = doc.get(MultivalueFieldUpdateProcessorFactory.this.sourceFieldName);
			if (inputField != null) {
				if ( inputField.getValueCount() > 1) {
					inputField.getValues().forEach(v -> {
						if (v != null) {
							analyzeAndAddValues(doc, v.toString());
						}
					});
				} else {
					analyzeAndAddValues(doc, inputField.getFirstValue().toString());
				}
			}
			super.processAdd(cmd);
		}
		
		private void analyzeAndAddValues(SolrInputDocument doc, String text) {
			TokenStream tokens = multivalueAnalyzer.tokenStream(MultivalueFieldUpdateProcessorFactory.this.destFieldName, text);
			CharTermAttribute charTermAtt = tokens.addAttribute(CharTermAttribute.class);
			try {
				tokens.reset();
				while (tokens.incrementToken()) {
					String term = charTermAtt.toString();
					if (!term.isEmpty()) {
						doc.addField(MultivalueFieldUpdateProcessorFactory.this.destFieldName, term);
					}
				}
				tokens.end();
				tokens.close();
			} catch (IOException e) {
				LOG.error("Error parsing multivalue field", e);
			}
		}

	}
}
