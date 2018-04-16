/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.db.lucene;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.search.SearchException;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocValuesRangeQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;

import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.page.Page;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.RangeQuery;
import com.baddata.db.SearchQuery;
import com.baddata.db.SortQuery;
import com.baddata.log.Logger;
import com.baddata.util.AppConstants;
import com.baddata.util.FileUtil;
import com.baddata.util.ReflectionUtil;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class SearchService {

	private static Logger logger = Logger.getLogger(SearchService.class.getName());

    // analyzers
    private Analyzer keywordAnalyzer = null;
    private Analyzer contentAnalyzer = null;

    // index reader map
    private Map<DbIndexType, DirectoryReader> indexReaderMap = new HashMap<DbIndexType, DirectoryReader>();
    private Map<DbIndexType, Integer> totalHitsMap = new HashMap<DbIndexType, Integer>();
    
    private static SearchService ref;
    
    /**
     * Singleton instance
     * @return
     */
    public static SearchService getInstance() {
        if (ref == null) {
            synchronized(SearchService.class) {
                if (ref == null) {
                    ref = new SearchService();
                }
            }
        }
        return ref;
    }

    // public constructor to ensure instantiated usage
    private SearchService() {
        keywordAnalyzer = new KeywordAnalyzer();
        contentAnalyzer = new StandardAnalyzer();
    }

    /**
     * Get the IndexSearcher
     * @param index
     * @return IndexSearcher
     * @throws CorruptIndexException
     * @throws IOException
     */
    private IndexSearcher getIndexSearcher(IndexPathInfo indexPathInfo) {
        File f = FileUtil.getLuceneIndex(indexPathInfo, true /*isSearch*/);
        if (f == null || !f.exists()) {
        		// no search index found, return null
            return null;
        }
        DbIndexType indexType = indexPathInfo.getDbIndexType();
        try {
	        Directory dir = NIOFSDirectory.open(f.toPath());
	
	        if ( DirectoryReader.indexExists(dir) ) {
	            // open the reader
	            DirectoryReader r = DirectoryReader.open(dir);
	            // put in a reader map to close it later
	            indexReaderMap.put(indexType, r);
	
	            return new IndexSearcher(r);
	        }
        } catch ( Exception e ) {
        	logger.error( "Failed to read and open db index for type " + indexType.getIndexId() + ".", e );
        }

        return null;
    }
    
    public Map<String, List<TypedObject>> getKeyToObjects(SearchSpec searchSpec, IndexPathInfo indexPathInfo) {
        
        //
        // Perform the SEARCH HERE
        //
        return this.searchIndex(indexPathInfo, searchSpec, null /*overridingKey*/);
    }

    /**
     * Begin search
     * @param searchRequest
     * @throws IOException
     * @throws ParseException
     */
    public Page search(SearchSpec searchSpec, IndexPathInfo indexPathInfo) {
        
    	DbIndexType indexType = indexPathInfo.getDbIndexType();
        Page page = new Page();
        
        if ( searchSpec == null ) {
            logger.error("Search faild, no search specification provided.", null);
            return page;
        } else if ( indexType == null ) {
            logger.error("Search failed, no index type provided.", null);
            return page;
        }
        
        //
        // update the index type related attributes
        //
        totalHitsMap.put(indexType, 0);

        long startTime = System.currentTimeMillis();
        
        //
        // Perform the SEARCH HERE
        //
        String resultKey = "key";
        Map<String, List<TypedObject>> searchResults = this.searchIndex(indexPathInfo, searchSpec, resultKey /*overridingKey*/);

        //
        // Build the Page result
        //
        Integer totalHits = totalHitsMap.get(indexType);
        totalHits = (totalHits == null) ? 0 : totalHits;
        
        int resultLimit = searchSpec.getLimit();
        // set the page results by the given index
        
        if (searchResults.size() > 0 ) {
            page.setItems( searchResults.get(resultKey) );
        } else {
            page.setItems( new ArrayList<TypedObject>() );
        }
        page.setLimit( resultLimit );
        page.setEllapsed( System.currentTimeMillis() - startTime );
        page.setPage( searchSpec.getPage() );
        page.setTotalHits(totalHits);
        
        if ( resultLimit > 1 ) {
            int totalPages = ( totalHits > 0 ) ? (int) Math.ceil( (float)totalHits / (float)resultLimit) : 0;
            page.setPages( totalPages );
        } else {
            page.setPages( resultLimit );
        }

        return page;

    }

    /**
     * Search an index
     * @param indexName
     * @param searchRequest
     * @throws Throwable
     */
    private Map<String, List<TypedObject>> searchIndex(IndexPathInfo indexPathInfo, SearchSpec searchSpec, String overridingKey) {
        Map<String, List<TypedObject>> resultMap = Maps.newHashMap();
    	DbIndexType indexType = indexPathInfo.getDbIndexType();
        IndexSearcher searcher = this.getIndexSearcher(indexPathInfo);

        if (searcher == null) {
            // no index, return empty
            resultMap.put(overridingKey, Lists.newArrayList());
            return resultMap;
        }

        Sort sort = null;
        List<Query> mustQueriesToAdd = new ArrayList<Query>();
        List<Query> shouldQueriesToAdd = new ArrayList<Query>();
        
        //
        // Build the sort query if we have it
        SortQuery sortQry = searchSpec.getSortQuery();
        if ( sortQry != null ) {
            Type sortType = sortQry.getSortType();
            String field = (sortQry.getField() != null) ? sortQry.getField().toLowerCase() : "createtime";
            boolean isAsc = sortQry.isAscending();
            
            SortField sortField = null;
            if ( sortType == Type.LONG ) {
                sortField = new SortedNumericSortField(field, sortType, !isAsc);
            } else {
                sortField = new SortField(field, sortType, !isAsc);
            }
            // set the Sort query
            sort = new Sort(sortField);
        }
        
        //
        // Build the range query if we have it
        List<RangeQuery> rangeQueries = searchSpec.getRangeQueries();
        if ( CollectionUtils.isNotEmpty(rangeQueries) ) {
            for ( RangeQuery rangeQry : rangeQueries ) {
                if ( rangeQry.getField() == null ) {
                    logger.error("Can't perform a range query without a field. Range query: " + rangeQry.toString(), null);
                    continue;
                }
                boolean isLongRange = rangeQry.isLongRange();
                String min = rangeQry.getMin();
                String max = rangeQry.getMax();
                String field = rangeQry.getField().toLowerCase();
                
                boolean includeLower = false;
                boolean includeUpper = false;
                Long maxLong = null;
                Long minLong = null;
                if ( StringUtils.isNotBlank(min) ) {
                    if ( isLongRange ) {
                        minLong = Long.parseLong(min);
                    }
                    includeLower = true;
                }
                if ( StringUtils.isNotBlank(max) ) {
                    if ( isLongRange ) {
                        maxLong = Long.parseLong(max);
                    }
                    includeUpper = true;
                }
                
                Query rangeQuery = null;
                
                if ( isLongRange) {
                    // it's a long value range query
                    rangeQuery = DocValuesRangeQuery.newLongRange( field, minLong, maxLong, includeLower, includeUpper );
                } else {
                    // it's a string (term) range query
                    rangeQuery = TermRangeQuery.newStringRange(field, min /*lowerTerm*/, max /*upperTerm*/, includeLower, includeUpper);
                }
                // it must occur to return a value
                mustQueriesToAdd.add(rangeQuery);
            }
        }
        
        if (searchSpec.getQueries() != null) {
            for (SearchQuery sq : searchSpec.getQueries()) {
                sq.init();

                // make sure there's a text query or it's a range query
                if (!sq.hasQuery()) {
                    continue;
                }

                String field = (sq.getField() != null) ? sq.getField().toLowerCase() : AppConstants.CONTENTS_FIELD;
                String qry = sq.getPattern();
                
                //
                // We can either have a wildcard query or a content query,
                // or a specific field query.
                //

                //
                // Is it an object ID query?
                if ( sq.getId() != null ) {
                    QueryParser qp = new QueryParser(AppConstants.ID_KEY.toLowerCase(), keywordAnalyzer);
                    try {
                        Query searchQuery = qp.parse(sq.getId().toString());
                        mustQueriesToAdd.add(searchQuery);
                    } catch ( Exception e ) {
                        logger.error( "Failed to parse and add a MUST-OCCUR boolean query for " + sq.getId().toString() + ".", e );
                    }
                //
                // Is it a parent object ID query?
                } else if ( sq.getParent() != null ) {
                    QueryParser qp = new QueryParser(AppConstants.PARENT_ID_KEY.toLowerCase(), keywordAnalyzer);
                    try {
                        Query searchQuery = qp.parse(sq.getParent().toString());
                        mustQueriesToAdd.add(searchQuery);
                    } catch ( Exception e ) {
                        logger.error( "Failed to parse and add a MUST-OCCUR boolean query for " + sq.getParent().toString() + ".", e );
                    }
                //
                // Is it a WILDCARD query?
                } else if ( sq.hasWildcard() ) {

                    //
                    // it's a wildcard query, split the words and create multiple
                    // boolean queries for each word
                    //

                    // substitute chars the standardtokenizer doesn't keep either
                    qry = qry.replaceAll("[-]", " ").replaceAll("\\s", " ");

                    // check if we have words
                    String[] words = qry.split("\\s");
                    for (String w : words) {
                        if (w.indexOf("*") != -1) {
                            // use the wildcard query
                            Query wildCardQuery = new WildcardQuery(new Term(AppConstants.CONTENTS_FIELD, w.trim()));
                            shouldQueriesToAdd.add(wildCardQuery);
                        } else {
                            if ( field == null ) {
                                logger.error("Can't perform a content query against a null specified field: " + sq.toString(), null);
                                continue;
                            }
                            // use a normal analyzer query
                        	QueryParser qp = new QueryParser(field, contentAnalyzer);
                        	try {
	                            Query searchQuery = qp.parse(w.trim());
	                            shouldQueriesToAdd.add(searchQuery);
                        	} catch ( Exception e ) {
                        		logger.error( "Failed to parse and add a SHOULD-OCCUR boolean query for " + w + ".", e );
                        	}
                        }
                    }

                //
                // or a CONTENTS query?
                } else if ( field != null && field.equals(AppConstants.CONTENTS_FIELD) ) {

                    // basic contents field query
                    QueryParser qp = new QueryParser(field, contentAnalyzer);
                    try {
	                    Query searchQuery = qp.parse(qry);
	                    shouldQueriesToAdd.add(searchQuery);
                    } catch ( Exception e ) {
                    	logger.error( "Failed to parse and add a SHOULD-OCCUR boolean query for " + qry + ".", e );
                    }
                
                //
                // Else it's a specific field query
                } else if ( field != null ){

                    // this is a specific field query. apply the keyword analyzer and it must occur to return a value
                    QueryParser qp = new QueryParser(field, keywordAnalyzer);
                    try {
	                    Query searchQuery = qp.parse(qry);
	                    mustQueriesToAdd.add(searchQuery);
                    } catch ( Exception e ) {
                    	logger.error( "Failed to parse and add a MUST-OCCUR boolean query for " + qry + ".", e );
                    }

                }
            }
        }

        // check if the search queries are empty/null, if so we're going to fetch all up to a limit
        if (mustQueriesToAdd.size() == 0 && shouldQueriesToAdd.size() == 0) {
            // no search queries were passed in, get all docs up to the page size
            QueryParser qp = new QueryParser(AppConstants.FOR_NAME_FIELD, keywordAnalyzer);
            String qry = indexType.getCanonicalName();
            try {
	            Query searchQuery = qp.parse(qry);
	            mustQueriesToAdd.add(searchQuery);
            } catch ( Exception e ) {
            	logger.error( "Failed to parse and add a MUST-OCCUR boolean query for " + qry + ".", e );
            }
        }
        
        BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();
        for ( Query q : mustQueriesToAdd ) {
            booleanQueryBuilder.add(q, Occur.MUST);
        }
        for ( Query q : shouldQueriesToAdd ) {
            booleanQueryBuilder.add(q, Occur.SHOULD);
        }
        BooleanQuery booleanQuery = booleanQueryBuilder.build();

        //
        // Search for the query and build search results
        //
        int pageSize = (searchSpec.getLimit() <= 0) ? AppConstants.MAX_SEARCH_LIMIT : searchSpec.getLimit();
        
        if ( searchSpec.fetchAll() ) {
            TopDocs topDocs = null;
            TotalHitCountCollector collector = new TotalHitCountCollector();
            try {
                // this is needed to populate the collector's "totalHits" count
                searcher.search(booleanQuery, collector);
                
                if ( sort != null ) {
                    topDocs = searcher.search( booleanQuery, Math.max(1, collector.getTotalHits()), sort );
                } else {
                    topDocs = searcher.search(booleanQuery, Math.max(1, collector.getTotalHits()));
                }
                
                if (topDocs != null) {
                    totalHitsMap.put(indexType, topDocs.totalHits);
                    
                    if (searchSpec.getLimit() > 0 && searchSpec.getPage() > 0) {
                        // return the correct page of results
                        
                        List<TypedObject> results = Lists.newArrayList();
                        resultMap.put(overridingKey, results);
                        
                        int startIndex = (searchSpec.getPage() - 1) * pageSize;
                        int endIndex = searchSpec.getPage() * pageSize;
                        endIndex = (topDocs.totalHits < endIndex) ? topDocs.totalHits : endIndex;

                        for (int i = startIndex; i < endIndex; i++) {
                            TypedObject resultObj = this.buildResult(topDocs.scoreDocs[i], searcher, indexPathInfo.getTenantId());
                            if (resultObj != null) {
                                results.add(resultObj);
                            }
                        }
                        return resultMap;
                    } else {
                        return this.buildResults(topDocs.scoreDocs, searcher, indexPathInfo.getTenantId(), overridingKey);
                    }
                }
                
                
            } catch ( Exception e ) {
                logger.error( "Search error: [booleanQuery: '" + booleanQuery.toString() + "', pageSize: '" + pageSize + "']", e );
            }
        } else if (sort != null) {
            
            TopFieldDocs topFieldDocs = null;
            try {
            	topFieldDocs = searcher.search(booleanQuery, pageSize, sort);
            } catch ( Exception e ) {
            	logger.error( "Search error: [booleanQuery: '" + booleanQuery.toString() + "', pageSize: '" + pageSize + "', sort: '" + sort.toString() + "']", e );
            }
            if ( topFieldDocs != null ) {
	            totalHitsMap.put(indexType, topFieldDocs.totalHits);
	            return this.buildResults(topFieldDocs.scoreDocs, searcher, indexPathInfo.getTenantId(), overridingKey);
            }
        } else {
            TopDocs topDocs = null;
            try {
            	topDocs = searcher.search(booleanQuery, pageSize);
            } catch ( Exception e ) {
            	logger.error( "Search error: [booleanQuery: '" + booleanQuery.toString() + "', pageSize: '" + pageSize + "']", e );
            }
            if ( topDocs != null ) {
                Integer totalHits = totalHitsMap.get(indexType);
                if ( totalHits == null ) {
                    totalHits = 0;
                }
	            totalHits += topDocs.totalHits;
	            totalHitsMap.put(indexType, totalHits);
	            return this.buildResults(topDocs.scoreDocs, searcher, indexPathInfo.getTenantId(), overridingKey);
            }
        }
        
        resultMap.put(overridingKey, Lists.newArrayList());
        
        return resultMap;
    }

    private Map<String, List<TypedObject>> buildResults(ScoreDoc[] scoreDocs, IndexSearcher searcher, String tenantId, String overridingKey)  {
        Map<String, List<TypedObject>> typedObjects = Maps.newHashMap();
        for (ScoreDoc scoreDoc : scoreDocs) {
			TypedObject classObj = this.buildResult(scoreDoc, searcher, tenantId);
			if (classObj != null) {
			    String key = (StringUtils.isNotBlank(overridingKey)) ? overridingKey : classObj.getKey();
			    List<TypedObject> objectsPerKey = typedObjects.get(key);
			    if (objectsPerKey == null) {
			        objectsPerKey = Lists.newArrayList();
			        typedObjects.put(key, objectsPerKey);
			    }
			    objectsPerKey.add(classObj);
			    // typedObjects.add(classObj);
			}
        }
        return typedObjects;
    }
    
    private TypedObject buildResult(ScoreDoc scoreDoc, IndexSearcher searcher, String tenantId) {
        TypedObject classObj = null;
        Document d;
        try {
            d = searcher.doc(scoreDoc.doc);
            classObj = this.constructObjectFromDoc(d, tenantId);
        } catch ( Exception e ) {
            logger.error( "Failed to build search result document from ScoreDoc '" + scoreDoc.toString() + "'.", e );
        }
        return classObj;
    }

    /**
     * Construct the Lucene document into a java bean
     * @param d
     * @return
     * @throws Throwable
     */
    @SuppressWarnings("unchecked")
    private TypedObject constructObjectFromDoc(Document d, String tenantId) {
        String forNameStr = d.get(AppConstants.FOR_NAME_FIELD);
        Object classObj = null;
        if (forNameStr != null) {
            // get the doc fields from the Lucene document
            List<IndexableField> docFields = d.getFields();

            try {
	            Class<?> cls = Class.forName(forNameStr);
	            classObj = cls.newInstance();
            } catch ( Exception e ) {
            	logger.error("Failed to construct document " + d.toString() + " into an EntityObject.", e );
            	return null;
            }

            if (docFields == null || docFields.size() == 0) {
                return (TypedObject) classObj;
            }
            
            Map<String, String> docFieldMap = Maps.newHashMap();
            for ( IndexableField iField : docFields ) {
                docFieldMap.put(iField.name().toLowerCase(), iField.name());
            }

            //
            // Use the forNameStr found in the doc to build a java class
            //

            List<Method> setterMethods = ReflectionUtil.getSetterMethods(classObj.getClass());

            // go through each setter method of this class
            for (Method setter : setterMethods) {

                // create the field name that matches what was used to create the doc's field
                String fieldName = ReflectionUtil.getFieldNameFromSetter(setter);
                
                String docFieldName = docFieldMap.get(fieldName.toLowerCase());
                if (docFieldName != null) {

                    // get the parameter types of the setter (should be only 1)
                    Class<?>[] paramTypes = setter.getParameterTypes();

                    if (paramTypes.length == 1) {
                        Class<?> paramType = paramTypes[0];
                        
                        // get the stored value
                        String value = d.get(docFieldName);
                        
                        //
                        // get the generics param type if it's a list/collection type
                        //
                        boolean isListValue = false;
                        if ( paramType.isAssignableFrom( List.class ) || paramType.isAssignableFrom( Collections.class ) ) {
                            isListValue = true;
                            java.lang.reflect.Type[] types = setter.getGenericParameterTypes();
                            if ( types != null && types.length > 0 ) {
                                ParameterizedType pType = (ParameterizedType) types[0];
                                java.lang.reflect.Type[] argTypes = pType.getActualTypeArguments();
                                if ( argTypes != null && argTypes.length > 0 ) {
                                    java.lang.reflect.Type argType = argTypes[0];
                                    
                                    //
                                    // Get the param type from the generics type
                                    //
                                    paramType = ((Class<?>)argType);
                                }
                            }
                        }

                        if ( value.startsWith(AppConstants.ORDERBY_SUB_OBJ_REF_KEY) ) {
                            //
                            // it has a sub-object, fetch it and set it
                            //
                        	try {
                        		Long innerObjRefId = Long.valueOf(value.substring(AppConstants.ORDERBY_SUB_OBJ_REF_KEY.length()));
                                DbIndexType innerObjIndexType = DbIndex.getIndexTypeByClass((Class<? extends TypedObject>) paramType);

                                //
                                // Search for the inner object
                                //
                                TypedObject innerObjVal = null;
                                try {
                                	IndexPathInfo indexPathInfo = new IndexPathInfo(AppConstants.LUCENE_STORE_NAME, tenantId, innerObjIndexType);
                                    innerObjVal = search(indexPathInfo, innerObjRefId);
                                } catch (SearchException e) {
                                    logger.error("Failed to perform inner object search against index '" + innerObjIndexType.getCanonicalName() + "'.", e );
                                }

                                if ( innerObjVal != null ) {
                                    try {
										setter.invoke(classObj, innerObjVal);
									} catch ( Exception e ) {
										logger.error( "Failed to set the sub-object's setter '" + setter.getName() + "' with entity '" + forNameStr + "'.", e );
									}
                                }
                        	} catch (Exception e) {
                        		logger.trace("Failed to get sub object ref id, reason: " + e.toString());
                        	}
                            
                        } else if ( value.startsWith(AppConstants.ORDERBY_SUB_OBJ_PARENT_REF_KEY) ) {
                            //
                            // look for the children with the parentId of this value
                            //
                            String innerObjRefId = value.substring(AppConstants.ORDERBY_SUB_OBJ_PARENT_REF_KEY.length());
                            DbIndexType innerObjIndexType = DbIndex.getIndexTypeByClass((Class<? extends TypedObject>) paramType);
                            
                            // get all of the child items by parent id
                            SearchSpec searchSpec = new SearchSpec(null /*userReferenceId*/);
                            SearchQuery query = new SearchQuery();
                            query.setParent(Long.valueOf(innerObjRefId));
                            searchSpec.setQueries(Lists.newArrayList(query));
                            
                            IndexPathInfo indexPathInfo = new IndexPathInfo(AppConstants.LUCENE_STORE_NAME, tenantId, innerObjIndexType);
                            String key = "key";
                            Map<String, List<TypedObject>> results = searchIndex(indexPathInfo, searchSpec, key);
                            
                            if ( results != null ) {
                                try {
                                    setter.invoke(classObj, results.get(key));
                                } catch ( Exception e ) {
                                    logger.error("Failed to set the '" + forNameStr + "' object's setter '" + setter.getName() + "' with value '" + value + "'.", e );
                                }
                            }
                            
                            //
                            // set the bean setter
                            //
                            try {
                                ReflectionUtil.setSetterMethodValue(setter, classObj, value, paramType);
                            } catch ( Exception e ) {
                                logger.error( "Failed to set the '" + forNameStr + "' object's setter '" + setter.getName() + "' with value '" + value + "'.", e );
                            }
                            
                        } else if ( isListValue ) {
                            //
                            // get the list of String values and convert them to the param type
                            //
                            List<Object> stuff = new ArrayList<Object>();
                            if ( value.indexOf(",") != -1 ) {
                                String[] vals = value.split(",");
                                if ( vals != null && vals.length > 0 ) {
                                    for ( String val : vals ) {
                                        if ( !Strings.isNullOrEmpty( val ) ) {
                                            Object trueValue = ReflectionUtil.getParamTypeValue( val, paramType );
                                            if ( trueValue != null ) {
                                                stuff.add(trueValue);
                                            }
                                        }
                                    }
                                }
                            }
                            if ( CollectionUtils.isNotEmpty( stuff ) ) {
                                try {
                                    //
                                    // set the setter with the list of converted objects
                                    setter.invoke(classObj, stuff);
                                } catch ( Exception e ) {
                                    logger.error("Failed to set an array list of items.", e );
                                }
                            }
                        } else {
                            //
                            // set the bean attribute
                            //
                            try {
								ReflectionUtil.setSetterMethodValue(setter, classObj, value, paramType);
							} catch ( Exception e ) {
								logger.error( "Failed to set the '" + forNameStr + "' object's setter '" + setter.getName() + "' with value '" + value + "'.", e );
							}
                        }
                    } // end paramTypes.length if check
                } // end docFieldName not null if
            } // end setterMethods for loop

        }
        return (TypedObject) classObj;
    }

    /**
     * Fetch a DTO by index type, field name, and query
     *
     * @param indexType
     * @param field index field to search the keyword against
     * @param keyword
     * @return EntityObject
     */
    public TypedObject search(IndexPathInfo indexPathInfo, String field, String keyword) throws SearchException {
        if ( field == null ) {
            throw new SearchException("The field argument cannot be null when searching by field and query");
        }
        field = field.toLowerCase();
        
        // create the query dto
        SearchQuery queryDTO = new SearchQuery();
        queryDTO.setField(field);
        queryDTO.setPattern(keyword);

        return search(indexPathInfo, Arrays.asList(queryDTO));
    }
    
    public Page getFirstPage(IndexPathInfo indexPathInfo, String field, String keyword) throws SearchException {
        if ( field == null ) {
            throw new SearchException("The field argument cannot be null when searching by field and query");
        }
        field = field.toLowerCase();
        
        // create the query dto
        SearchQuery queryDTO = new SearchQuery();
        queryDTO.setField(field);
        queryDTO.setPattern(keyword);
        return getFirstPage(indexPathInfo, Arrays.asList(queryDTO));
    }

    /**
     * Find DTO by a reference
     * @param indexType
     * @param reference
     * @return EntityObject
     */
    public TypedObject search(IndexPathInfo indexPathInfo, Long reference) throws SearchException {
        SearchQuery queryDTO = new SearchQuery();
        queryDTO.setId(reference);
        return search(indexPathInfo, Arrays.asList(queryDTO));
    }
    
    public TypedObject searchByParentId(IndexPathInfo indexPathInfo, Long reference) throws SearchException {
        SearchQuery queryDTO = new SearchQuery();
        queryDTO.setParent(reference);
        return search(indexPathInfo, Arrays.asList(queryDTO));
    }

    public TypedObject search(IndexPathInfo indexPathInfo, List<SearchQuery> queries) {

    	TypedObject obj = null;

        // send a page size of 1
        Page response = search(indexPathInfo, queries, 1 /* pageSize */, 1 /* page */);
        if (response != null && response.getItemCount() == 1) {
            // get result
            obj = (TypedObject) response.getDataAt(0);
        }

        return obj;
    }
    
    public Page getFirstPage(IndexPathInfo indexPathInfo, List<SearchQuery> queries) {
        // send a page size of 1
        return search(indexPathInfo, queries, 1 /* pageSize */, 1 /* page */);
    }

    private Page search(IndexPathInfo indexPathInfo, List<SearchQuery> queries, int pageSize, int page) {
        // create the search request
        SearchSpec spec = new SearchSpec(null /*userReferenceId*/);
        spec.setQueries(queries);
        spec.setLimit(pageSize);
        spec.setPage(page);

        return this.searchAndReturnPage( indexPathInfo, spec );
    }
    
    public Page search(IndexPathInfo indexPathInfo) {
        return search(indexPathInfo, 1 /* pageSize */, 1 /* page */);
    }

    public Page search(IndexPathInfo indexPathInfo, int pageSize, int page) {
        SearchQuery searchQueryDTO = new SearchQuery();
        searchQueryDTO.setField(AppConstants.TYPE_ID_KEY);
        searchQueryDTO.setPattern(indexPathInfo.getDbIndexType().getIndexId());
        List<SearchQuery> queries = Arrays.asList(searchQueryDTO);

        // create the search request
        SearchSpec spec = new SearchSpec(null /*userReferenceId*/);
        spec.setQueries(queries);
        spec.setLimit(pageSize);
        spec.setPage(page);
        
        return this.searchAndReturnPage( indexPathInfo, spec );
    }
    
    private Page searchAndReturnPage(IndexPathInfo indexPathInfo, SearchSpec spec) {
    	Page response = new Page();
        response = this.search( spec, indexPathInfo );
        return response;
    }

    /**
     * Helper to get the number of documents by index type
     * 
     * @param indexType
     * @return int is the number of docs per specified index type
     */
    public int getNumDocsByIndex(IndexPathInfo indexPathInfo) {
        IndexSearcher searcher = this.getIndexSearcher(indexPathInfo);
        if (searcher != null) {
            return searcher.getIndexReader().numDocs();
        }
        return 0;
    }
}
