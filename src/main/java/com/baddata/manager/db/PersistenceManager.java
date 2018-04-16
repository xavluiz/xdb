/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.manager.db;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.search.SearchException;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.SortField.Type;
import org.joda.time.DateTime;

import com.baddata.api.dto.DbIndexInfo;
import com.baddata.api.dto.TypedObject;
import com.baddata.api.dto.page.Page;
import com.baddata.api.dto.salesforce.SalesforceOauth2Creds;
import com.baddata.api.dto.user.User;
import com.baddata.api.query.SearchSpec;
import com.baddata.db.DbIndex;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.db.SearchQuery;
import com.baddata.db.SortQuery;
import com.baddata.db.lucene.IndexPathInfo;
import com.baddata.db.lucene.IndexerService;
import com.baddata.db.lucene.SearchService;
import com.baddata.exception.ApiServiceException;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.Logger;
import com.baddata.util.AppConstants;
import com.baddata.util.ReflectionUtil;
import com.baddata.util.StringUtil;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class PersistenceManager {
	
	private static Logger logger = Logger.getLogger(PersistenceManager.class.getName());
	
	private static PersistenceManager singleton;
	
	private SearchService searchSvc;
	private IndexerService indexSvc;
	private Map<DbIndexType, Long> idMap = new HashMap<DbIndexType, Long>();
	
	public static PersistenceManager getInstance() {
		if ( singleton == null ) {
			synchronized (PersistenceManager.class) {
				if ( singleton == null ) {
					singleton = new PersistenceManager();
				}
			}
		}
		return singleton;
	}
	
	@SuppressWarnings("unchecked")
	private PersistenceManager() {
		searchSvc = SearchService.getInstance();
		indexSvc = IndexerService.getInstance();
        
        //
        // Build the idMap with latest count of any existing index.
        // The "createId" function will use the value greater than the latest count
        // as an id to allow searching (sorting or filtering) by sequence number.
        //
        Page p = this.getPage(DbIndexType.DB_INDEX_INFO_TYPE);
        if ( p != null && p.getTotalHits() > 0 ) {
            List<TypedObject> typedObjects = (List<TypedObject>) p.getItems();
            if ( CollectionUtils.isNotEmpty(typedObjects) ) {
                for ( TypedObject indexInfo : typedObjects ) {
                	DbIndexInfo dbIndexInfo = (DbIndexInfo) indexInfo;
                	// update the idMap
                    idMap.put(DbIndexType.getTypeById(dbIndexInfo.getIndexName()), dbIndexInfo.getDocCount());
                }
            }
        }
        
        this.init();
	}
	
	private void init() {
	    //
	}
    
    /**
     * Create a sequence ID for the specified index. This will be the next
     * sequence number for that index.
     * 
     * @param indexType
     * @return String is the sequence number as a String
     */
    public Long createId(DbIndexType indexType) {
        synchronized (indexType) {
            Long value = idMap.get(indexType);
            if ( value == null ) {
                // it doesn't exist yet, set it to 1
                value = 1L;
            } else {
                // increment the value
                value += 1;
            }
            idMap.put(indexType, value);
            return value;
        }
    }

	/**
     * This will look at the Persistent Object id and decide
     * whether it needs to create or update if it's null/empty
     *
     * @return String is the object id Id
     * @throws ApiServiceException
     */
    public Long create(TypedObject newEntity) throws IndexPersistException {
        return this.create(newEntity, false /*isIndexerRequest*/);
    }
	public Long create(TypedObject newEntity, boolean isIndexerRequest) throws IndexPersistException {
	    if ( newEntity == null) {
	        throw new IndexPersistException("Invalid content provided to create.");
	    } else if ( newEntity.getId() != null ) {
	        throw new IndexPersistException( "Cannot create. Content already contains an identity id." );
	    }
	    return this.save(newEntity, isIndexerRequest);
	}
	
	public void update(TypedObject existingEntity) throws IndexPersistException {
	    this.update(existingEntity, false /*isIndexerRequest*/);
	}
	
	public void update(TypedObject existingEntity, boolean isIndexerRequest) throws IndexPersistException {
	    if ( existingEntity == null || existingEntity.getId() == null ) {
	        throw new IndexPersistException("Cannot update. No identity id provided");
	    }
	    this.save(existingEntity, isIndexerRequest);
	}
	
	public void createEntityWithProvidedId(TypedObject entity) throws IndexPersistException {
	    IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(entity);
        synchronized (indexPathInfo.getDbIndexType()) {
            indexSvc.create(entity, indexPathInfo, false /*isIndexerRequest*/);
        }
    }
	
	/**
	 * This will determine if the TypedObject needs to be created or updated
	 * based on whether the id is null or not.
	 * 
	 * @param entity
	 * @return
	 * @throws IndexPersistException
	 */
	public Long save(TypedObject entity) throws IndexPersistException {
	    return this.save(entity, false /*isIndexerRequest*/);
	}
	public Long save(TypedObject entity, boolean isIndexerRequest) throws IndexPersistException {
        if ( entity == null ) {
            throw new IndexPersistException("Failed to persist the null entity");
        }
        
        IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(entity);
        
    	Long id = entity.getId();

        if (id == null) {
            // No ID found, is a simple create
            this.createEntity(entity, indexPathInfo, isIndexerRequest);
        } else {
            // ID found, it still may be a create but most likely an update
        	TypedObject existing;
            try {
                existing = searchSvc.search(indexPathInfo, id);
                // DbIndexType indexType = indexSvc.getIndexId(entity);
                // existing = this.getById(indexType, id);
                
                DateTime now = DateTime.now();
                if ( existing != null ) {
	                // Merge the existing non-null properties with any
                		// properties that are null in the saving object.
	                this.merge(existing, entity);
                } else {
                		// set the update and create date
                    entity.setCreateTime( now );
                }

                // update the updatedate
                entity.setUpdateTime( now );

                // update
                indexSvc.update( entity, indexPathInfo, isIndexerRequest);

            } catch (Exception e) {
                logger.error("Failed to find an existing record to update using id '" + id + "' in index '" + indexPathInfo.getDbIndexType().getCanonicalName() + "'.", e);
                // create instead
                this.createEntity(entity, indexPathInfo, isIndexerRequest);
            }
        }
        
        id = entity.getId();
        
        return id;
    }
    
    public void createEntities(List<? extends TypedObject> entities, DbIndexType indexType) throws IndexPersistException {
    	IndexPathInfo indexPathInfo = null;
        if ( entities != null ) {
            for ( TypedObject entity : entities ) {
	            	if ( indexPathInfo == null ) {
	            		// create the index path
	            		indexPathInfo = new IndexPathInfo(AppConstants.LUCENE_STORE_NAME, entity.getTenantId(), indexType);
	            	}
                this.initializeNewEntity(entity, indexType);
            }
        }
        indexSvc.createBatch(entities, indexPathInfo, false /*isIndexerRequest*/);
    }
    
    public void updateEntities(List<? extends TypedObject> entities, DbIndexType indexType) throws IndexPersistException {
        IndexPathInfo indexPathInfo = null;
        if ( entities != null ) {
            for ( TypedObject entity : entities ) {
                if ( indexPathInfo == null ) {
                    // create the index path
                    indexPathInfo = new IndexPathInfo(AppConstants.LUCENE_STORE_NAME, entity.getTenantId(), indexType);
                    break;
                }
            }
        }
        indexSvc.updateBatch(entities, indexPathInfo, false /*isIndexerRequest*/);
    }
    
    //------------------------------------------------------------------------------------------
    // Begin DELETE methods
    //------------------------------------------------------------------------------------------
    
    public void delete(TypedObject entity) throws IndexPersistException, SearchException {
    		Long id = entity.getId();
    		String entityName = entity.getClass().getName();
        if ( id == null ) {
        	String nullErrMsg = "Reference is not available for delete request: " + entityName;
        	logger.error(nullErrMsg, null);
            throw new IndexPersistException(nullErrMsg);
        }

        DbIndexType indexType = DbIndex.getIndexId( entity );
        if (indexType == null) {
            throw new IndexPersistException("Data index type is not available for delete request: " + entityName);
        }
        
        String tenantId = ( StringUtils.isNotBlank(entity.getTenantId()) ) ? entity.getTenantId() : null;

        this.delete( indexType, id, tenantId, false /*isIndexerRequest*/ );
    }
    
    /**
     * This is currently only used by objects that actually have a tenantId, such as DbIndexType.SALESFORCE_OPPORTUNITY_TYPE
     * @param indexType
     * @param tenantId
     * @throws IndexPersistException
     * @throws SearchException
     */
    public void deleteObjectsByIndexType(DbIndexType indexType, String tenantId) throws IndexPersistException, SearchException {
	    	IndexPathInfo indexPathInfo = new IndexPathInfo(AppConstants.LUCENE_STORE_NAME, tenantId, indexType);
	    	List<? extends TypedObject> result = this.getAllForIndexByIndexPath(indexPathInfo);
	    	if ( CollectionUtils.isNotEmpty(result) ) {
	    		// delete them as a batch
	    		List<Long> refIds = Lists.newArrayList();
	    		for ( TypedObject obj : result ) {
	    			refIds.add(((TypedObject)obj).getId());
	    		}
	    		indexSvc.delete(indexPathInfo, refIds, false /*isIndexerRequest*/);
	    	}
    }
    
    public void deleteObjectsByUserRef(DbIndexType indexType, Long userRef) throws IndexPersistException {
    		IndexPathInfo indexPathInfo = new IndexPathInfo(AppConstants.LUCENE_STORE_NAME, indexType);
    		List<? extends TypedObject> result = this.getAllForUserRef(indexType, userRef);
    		if ( CollectionUtils.isNotEmpty(result) ) {
    			// delete them as a batch
	    		List<Long> refIds = Lists.newArrayList();
	    		for ( TypedObject obj : result ) {
	    			refIds.add(((TypedObject)obj).getId());
	    		}
	    		indexSvc.delete(indexPathInfo, refIds, false /*isIndexerRequest*/);
    		}
    }
    
    //------------------------------------------------------------------------------------------
    // End DELETE methods
    //------------------------------------------------------------------------------------------
    
    public Map<String, List<TypedObject>> getKeyToObjectsSortBy(DbIndexType indexType, String sortByField, String tenantId) {
        IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(indexType, tenantId);
        
        // get them all and sort by the field specified
        // in ascending order (i.e. 2017-10-01, 2017-11-01, 2017-12-01, etc...)
        SearchSpec searchSpec = new SearchSpec(true /*getAll*/);
        // use the sort query
        SortQuery sortQuery = new SortQuery();
        sortQuery.setField(sortByField.toLowerCase());
        searchSpec.setSortQuery(sortQuery);
        
        return searchSvc.getKeyToObjects(searchSpec, indexPathInfo);
    }
    
    public Map<String, List<TypedObject>> getKeyToObjects(DbIndexType indexType, String tenantId) {
        IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(indexType, tenantId);
        
        // get them all
        SearchSpec searchSpec = new SearchSpec(true /*getAll*/);
        SortQuery sortQuery = new SortQuery();
        sortQuery.setField(AppConstants.SORT_ID_KEY);
        sortQuery.setIsAscending(true);
        searchSpec.setSortQuery(sortQuery);
        
        return searchSvc.getKeyToObjects(searchSpec, indexPathInfo);
    }
    
    public TypedObject getFirstOne( DbIndexType indexType, SearchSpec searchSpec, String tenantId ) {
        IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(indexType, tenantId);
        searchSpec.setLimit(1);
        Page p = searchSvc.search(searchSpec, indexPathInfo);
        if (CollectionUtils.isNotEmpty(p.getItems())) {
            return (TypedObject) p.getItems().get(0);
        }
        return null;
    }
    
    public Page get( DbIndexType indexType, SearchSpec searchSpec ) {
    	return this.get(indexType, searchSpec, searchSpec.getTenantId());
    }
    
    public Page get( DbIndexType indexType, SearchSpec searchSpec, String tenantId ) {
        IndexPathInfo indexPathInfo = null;
        if ( indexType.hasTenantIndex() ) {
            indexPathInfo = indexSvc.buildIndexPathInfo(indexType, tenantId);
        } else {
            indexPathInfo = indexSvc.buildIndexPathInfo(indexType, null);
        }
        return searchSvc.search(searchSpec, indexPathInfo);
    }
    
    public Page getPage( DbIndexType indexType, String field, String keyword, String tenantId ) {
        IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(indexType, tenantId);
        try {
            return searchSvc.getFirstPage(indexPathInfo, field, keyword);
        } catch (SearchException e) {
            logger.error("Failed to fetch page data in '" + indexType.getCanonicalName() + "' by field '" + field + "' with keyword '" + keyword + "'.", e);
        }
        return null;
    }
    
    public Page get(DbIndexType indexType, int pageSize, int page) {
    	return this.get(indexType, pageSize, page, null /*tenantId*/);
    }
    
    private Page get(DbIndexType indexType, int pageSize, int page, String tenantId) {
        IndexPathInfo indexPathInfo = null;
        if ( indexType.hasTenantIndex() ) {
            indexPathInfo = indexSvc.buildIndexPathInfo(indexType, tenantId);
        } else {
            indexPathInfo = indexSvc.buildIndexPathInfo(indexType, null);
        }
    	return searchSvc.search(indexPathInfo, pageSize, page);
    }
    
    public TypedObject getById(DbIndexType indexType, Long id) {
        return this.get(indexType, AppConstants.ID_KEY, String.valueOf(id), null /*tenantId*/);
    }
    
    public TypedObject getById(DbIndexType indexType, Long id, String tenantId) {
        return this.get(indexType, AppConstants.ID_KEY, String.valueOf(id), tenantId);
    }
    
    public TypedObject get(DbIndexType indexType, String field, String keyword) {
    	return this.get(indexType, field, keyword, null /*tenantId*/);
    }
    
    public TypedObject getByUserRef(DbIndexType indexType, long userRef) {
        return this.get(indexType, AppConstants.USER_REFERENCE_KEY, String.valueOf(userRef), null /*tenantId*/);
    }
    
    public TypedObject getByUserRef(DbIndexType indexType, long userRef, String tenantId) {
        return this.get(indexType, AppConstants.USER_REFERENCE_KEY, String.valueOf(userRef), tenantId);
    }
    
    public TypedObject getSalesforceOauthByInstanceTenantId(DbIndexType indexType, String instanceTenantId) {
        SearchSpec searchSpec = new SearchSpec();
        
        SearchQuery q1 = new SearchQuery();
        q1.setField("instancetenantid");
        q1.setPattern(StringUtil.buildExactMatchKeyword(instanceTenantId));
        
        searchSpec.setQueries(Arrays.asList(q1));
        
        return this.getFirstOne(indexType, searchSpec, null /*tenantId*/);
    }
    
    public TypedObject get(DbIndexType indexType, String field, String keyword, String tenantId) {
        if ( StringUtils.isBlank(keyword) ) {
            // not allowed to search against a field using a null or blank keyword
            return null;
        }
        
	    	IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(indexType, tenantId);
	    	try {
	            return searchSvc.search(indexPathInfo, field, StringUtil.buildExactMatchKeyword(keyword));
	        } catch (SearchException e) {
	            logger.error("Failed to fetch a typed object in '" + indexType.getCanonicalName() + "' by field '" + field + "' with keyword '" + keyword + "'.", e);
	        }
	    	return null;
    }
    
    public List<? extends TypedObject> getAllForObjectByFieldAndKeyword(DbIndexType indexType, String field, String keyword, String tenantId) {
        
        SearchSpec searchSpec = new SearchSpec(true /*getall*/);
        
        SearchQuery query = new SearchQuery();
        query.setField(field);
        query.setPattern(keyword);
        searchSpec.setQueries(Arrays.asList(query));
        
        return this.getAllForObjectBySearchSpec(indexType, searchSpec, tenantId);
    }
    
    public List<? extends TypedObject> getAllForObjectBySearchSpec(DbIndexType indexType, SearchSpec searchSpec, String tenantId) {
        IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(indexType, tenantId);
        
        //
        // Since we're getting all, make sure we set the search spec fetchAll to true
        if (!searchSpec.fetchAll()) {
            searchSpec.setFetchAll(true);
        }

        Page p = searchSvc.search(searchSpec, indexPathInfo);
        if ( p != null && p.getItems() != null ) {
            return (List<TypedObject>) p.getItems();
        }
        
        return Lists.newArrayList();
    }
    
    /**
     * Fetch all results in 1 go.  This should only be used on results less than say... 5,000
     * @param indexType
     * @return
     */
    private List<? extends TypedObject> getAllForIndexByIndexPath(IndexPathInfo indexPathInfo) {
        SearchSpec searchSpec = new SearchSpec(true /*getall*/);
        
        SearchQuery query = new SearchQuery();
        query.setField("typeid");
        query.setPattern(indexPathInfo.getDbIndexType().getIndexId());
        searchSpec.setQueries(Arrays.asList(query));
        
        List<TypedObject> result = new ArrayList<TypedObject>();
        Page p = searchSvc.search(searchSpec, indexPathInfo);
        if ( p != null && p.getItemCount() > 0 ) {
            result = (List<TypedObject>) p.getItems();
        }
        
        return result;
    }
    
    public List<? extends TypedObject> getAllForKeywordFromContent(DbIndexType indexType, String keyword) {
        SearchSpec searchSpec = new SearchSpec(true /*getall*/);
        
        SearchQuery query = new SearchQuery();
        query.setField(AppConstants.CONTENTS_FIELD);
        query.setPattern(keyword);
        searchSpec.setQueries(Arrays.asList(query));
        
        IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(indexType, null /*tenantId*/);
        List<TypedObject> result = new ArrayList<TypedObject>();
        Page p = searchSvc.search(searchSpec, indexPathInfo);
        if ( p != null && p.getItemCount() > 0 ) {
            result = (List<TypedObject>) p.getItems();
        }
        
        return result;
    }
    
    public List<? extends TypedObject> getAllForUserRef(DbIndexType indexType, Long userRef) {
        return this.getAllForObjectByFieldAndKeyword(indexType, AppConstants.USER_REFERENCE_KEY, String.valueOf(userRef));
    }
    
    public List<? extends TypedObject> getAllForIndex(DbIndexType indexType, String tenantId) {
        return this.getAllForObjectByFieldAndKeyword(indexType, "typeid", indexType.getIndexId(), tenantId);
    }
    
    /**
     * Paginate through the total results and return 1 big list.
     * @param indexType
     * @param field
     * @param keyword
     * @return
     */
    public List<? extends TypedObject> getAllForObjectByFieldAndKeyword(DbIndexType indexType, String field, String keyword) {
    	return this.getAllForObjectByFieldAndKeyword(indexType, field, keyword, null /*tenantId*/);
    }
    
    public TypedObject getLatestObject(DbIndexType indexType, String tenantId) {
        SearchSpec spec = buildObjectSortedBySearchSpec("createdDate", Type.LONG, false /*ascending*/);
        return fetchUsingSortedByQuery(indexType, spec, tenantId);
    }
    
    public TypedObject getYoungestObjectSortedBy( DbIndexType indexType, String sortByField, Type sortByType, String tenantId ) {
        SearchSpec spec = buildObjectSortedBySearchSpec(sortByField, sortByType, false /*ascending*/);
        return fetchUsingSortedByQuery(indexType, spec, tenantId);
    }
    
    public TypedObject getOldestObjectSortedBy( DbIndexType indexType, String sortByField, Type sortByType, String tenantId ) {
        SearchSpec spec = buildObjectSortedBySearchSpec(sortByField, sortByType, true /*ascending*/);
        return fetchUsingSortedByQuery(indexType, spec, tenantId);
    }
    
    public Page getPage( DbIndexType indexType ) {
    	IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(indexType, null /*tenantId*/);
        SearchSpec searchSpec = new SearchSpec(null /*userReferenceId*/);
        searchSpec.setLimit(AppConstants.MAX_SEARCH_LIMIT);
        searchSpec.setPage(1);
        return searchSvc.search(searchSpec, indexPathInfo);
    }
    
    public TypedObject getFirstObject(DbIndexType indexType, String tenantId) {
        IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(indexType, tenantId);
        SearchSpec searchSpec = new SearchSpec(null /*userReferenceId*/);
        searchSpec.setLimit(1);
        searchSpec.setPage(1);
        Page p = searchSvc.search(searchSpec, indexPathInfo);
        if ( p != null && p.getItemCount() > 0 ) {
            return (TypedObject) p.getItems().get(0);
        }
        return null;
    }
    
    public int getNumDocsByIndex(DbIndexType indexType, String tenantId) {
    	IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(indexType, tenantId);
        return searchSvc.getNumDocsByIndex(indexPathInfo);
    }
    
    //------------------------------------------------------------------------------------------
    // Custom Persistence Methods
    //------------------------------------------------------------------------------------------
    
    /**
     * Fetch a user by username
     * 
     * @param username
     * @return User
     */
    public User getUserByUsername(String username) {
        // check to see if this user exists
        User user = (User) this.get(DbIndexType.USER_TYPE, "username", username, null /*tenantId*/ );
        return user;
    }
    
    public Set<String> getExistingSalesforceOauthInstanceIds(boolean lowercaseIds) {
        Set<String> existingOauthInstanceIds = Sets.newHashSet();
        
        List<SalesforceOauth2Creds> credsList = (List<SalesforceOauth2Creds>)
                PersistenceManager.getInstance().getAllForIndex(DbIndexType.SALESFORCE_OAUTH2_CREDS_TYPE, null /*tenantId*/);
        if (credsList != null) {
            for ( SalesforceOauth2Creds creds : credsList ) {
                if (lowercaseIds) {
                    existingOauthInstanceIds.add(creds.getDynamicInstanceTenantId().toLowerCase());
                } else {
                    // as is
                    existingOauthInstanceIds.add(creds.getDynamicInstanceTenantId());
                }
            }
        }
        return existingOauthInstanceIds;
    }
    
    //------------------------------------------------------------------------------------------
    // PRIVATE METHODS
    //------------------------------------------------------------------------------------------
    
    private void createEntity(TypedObject entity, IndexPathInfo indexPathInfo, boolean isIndexerRequest) throws IndexPersistException {
        synchronized (indexPathInfo.getDbIndexType()) {
            this.initializeNewEntity(entity, indexPathInfo.getDbIndexType());

            indexSvc.create(entity, indexPathInfo, isIndexerRequest);
        }
    }
    
    private void delete(DbIndexType indexType, Long id, String tenantId, boolean isIndexerRequest) throws IndexPersistException, SearchException {
        IndexPathInfo indexPathInfo = indexSvc.buildIndexPathInfo(indexType, tenantId);
        TypedObject existingObj = this.getById(indexType, id, tenantId);
        
        if ( existingObj != null ) {
            
            String objIdInfo = existingObj.toString() + "-" + id;
            
            //
            // go through the attributes to see if there are any of type "TypedObject"
            // and delete them if so.
            //
            List<Method> getterMethods = ReflectionUtil.getGetterMethods( existingObj.getClass() );
            if ( CollectionUtils.isNotEmpty( getterMethods ) ) {
                for ( Method getter : getterMethods ) {
                    // check if it's assignable to the TypedObject class
                    if (getter.getReturnType().isAssignableFrom(TypedObject.class)) {
                        String canonicalName = getter.getReturnType().getCanonicalName();
                        try {
                            Class<?> theClass = Class.forName(canonicalName);
                            // check if it's of type TypedObject
                            if (ReflectionUtil.isClassOfBase(theClass, TypedObject.class)) {
                                TypedObject innerObj = (TypedObject) ReflectionUtil.getGetterMethodValue(getter, existingObj);
                                // check if it's a valid value
                                if ( innerObj != null ) {
                                    // delete the inner object
                                    DbIndexType innerObjIndextype = DbIndex.getIndexId( innerObj );
                                    this.delete( innerObjIndextype, innerObj.getId(), tenantId, isIndexerRequest );
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Unable to find the index type of the sub-object: " + canonicalName, null);
                        }
                    }
                }
            }
            
            indexSvc.delete(indexPathInfo, id, isIndexerRequest);
        }
    }
    
    private SearchSpec buildObjectSortedBySearchSpec(String sortByField, Type sortByType, boolean ascending) {
        SearchSpec spec = new SearchSpec(null /*userReferenceId*/);
        SortQuery sortQuery = new SortQuery();
        sortQuery.setField(sortByField);
        sortQuery.setIsAscending(ascending);
        sortQuery.setSortType(sortByType);
        spec.setSortQuery(sortQuery);
        spec.setLimit(1);
        
        return spec;
    }
    
    private TypedObject fetchUsingSortedByQuery(DbIndexType indexType, SearchSpec spec, String tenantId) {
        Page p = this.get(indexType, spec, tenantId);
        if ( p != null ) {
            // get data at is null safe, it'll return null
            // if there are no items
            return (TypedObject) p.getDataAt(0);
        }
        return null;
    }
    
    private void initializeNewEntity(TypedObject entity, DbIndexType indexType) {
        // create
        entity.setId( this.createId(indexType) );
        // set the update and create date
        entity.setCreateTime( new DateTime(DateTime.now()) );
        entity.setUpdateTime( new DateTime(DateTime.now()) );
    }
    
    /**
     * Merge existing with this object
     *
     * @param existing
     * @param indexType
     * @throws ApiServiceException
     */
    private void merge(TypedObject existingObj, TypedObject newObj) throws IndexPersistException {
        Class<?> existingClass = existingObj.getClass();
        Class<?> classToUpdate = newObj.getClass();

        Set<String> processedProperties = new HashSet<String>();

        // get the methods
        List<Method> getterMethods = ReflectionUtil.getGetterMethods(existingClass);

        // go through the methods and merge the existing with the incoming object
        for (Method m : getterMethods) {
            String propName = ReflectionUtil.getGetterFieldName(m);
            processedProperties.add(propName);

            //
            // Make sure we skip merging the values that should not be changed by the user
            // ID_KEY, TYPE_ID_KEY, CREATE_DATE_KEY, UPDATE_DATE_KEY, PARENT_ID_KEY, TENANT_ID_KEY, USER_REFERENCE_KEY
            if (propName.equalsIgnoreCase(AppConstants.ID_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.CREATE_DATE_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.UPDATE_DATE_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.TYPE_ID_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.PARENT_ID_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.TENANT_ID_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.USER_REFERENCE_KEY) ||
                    propName.equalsIgnoreCase("class")) {
                // don't merge any of these
                continue;
            }

            // is the existing value for newObj getter null?
            Object existingValue = ReflectionUtil.getGetterMethodValue(m, existingObj);
            if (existingValue == null) {
                continue;
            }

            // is the incoming value for newObj getter null or empty? set it to the existing if so
            Object newObjVal = ReflectionUtil.getGetterMethodValue(m, newObj);
            if (newObjVal == null || Strings.isNullOrEmpty(newObjVal.toString())) {
                // set the existing value to the incoming value
                Method setterMethod = ReflectionUtil.getSetterMethodFromFieldName(classToUpdate, propName);
                try {
                    setterMethod.invoke(newObj, existingValue);
                } catch (Throwable e) {
                    throw new IndexPersistException("Unable to merge the existing object with the incoming object for property '" + propName + "'.", e);
                }
            } else if (newObjVal instanceof String && newObj.toString().equals(AppConstants.UNSET_STRING_VALUE)) {
                // unset the value
                Method setterMethod = ReflectionUtil.getSetterMethodFromFieldName(classToUpdate, propName);
                try {
                    setterMethod.invoke(newObj, "");
                } catch (Throwable e) {
                    throw new IndexPersistException("Unable to merge the existing object with the incoming object for property '" + propName + "'.", e);
                }
            }
        }

        // get the fields in case there are fields without a getter
        Field[] fields = ReflectionUtil.getBeanFields(existingClass);

        // go through the public bean fields
        for (Field f : fields) {
            String propName = f.getName();
            // check if has already been processed or it's a reserved property
            if (processedProperties.contains(propName) ||
                    propName.equalsIgnoreCase(AppConstants.ID_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.CREATE_DATE_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.UPDATE_DATE_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.TYPE_ID_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.PARENT_ID_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.TENANT_ID_KEY) ||
                    propName.equalsIgnoreCase(AppConstants.USER_REFERENCE_KEY) ||
                    propName.equalsIgnoreCase("class")) {
                // don't merge any of these
                continue;
            }

            // is the existing value for newObj field null?
            Object existingValue = ReflectionUtil.getFieldValue(f, existingObj);
            if (existingValue == null) {
                continue;
            }

            // is newObj value for this field null or empty
            Object newObjVal = ReflectionUtil.getFieldValue(f, newObj);
            if (newObjVal == null || Strings.isNullOrEmpty(newObjVal.toString())) {
                // set the existing value to existing value
                try {
                    f.set(newObj, existingValue);
                } catch (Throwable e) {
                    throw new IndexPersistException("Unable to merge the existing with the incoming object.", e);
                }
            }
        }
    }
    
}
