/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.db.lucene;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.mail.search.SearchException;
import javax.xml.bind.annotation.XmlEnum;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.joda.time.DateTime;

import com.baddata.annotation.ApiDataInfo;
import com.baddata.api.dto.DbIndexInfo;
import com.baddata.api.dto.TypedObject;
import com.baddata.db.DbIndex.DbIndexType;
import com.baddata.exception.ApiServiceException;
import com.baddata.exception.IndexPersistException;
import com.baddata.log.Logger;
import com.baddata.manager.db.PersistenceManager;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;
import com.baddata.util.Condition;
import com.baddata.util.FileUtil;
import com.baddata.util.ReflectionUtil;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;

public class IndexerService {

	private static Logger logger = Logger.getLogger(IndexerService.class.getName());

	private static String INDEX_WRITE_LOCK = "write.lock";
    private static IndexerService ref;
    
    private Map<DbIndexType, IndexWorker> indexWorkerMap = Maps.newConcurrentMap();
    private Map<String, IndexPathInfo> indexPathInfoByIndexId = Maps.newConcurrentMap();
    
    private AtomicBoolean haltIndexing = new AtomicBoolean(false);

    /**
     * Singleton instance
     * @return
     */
    public static IndexerService getInstance() {
        if (ref == null) {
            synchronized(IndexerService.class) {
                if (ref == null) {
                    ref = new IndexerService();
                }
            }
        }
        return ref;
    }
    
    public void stop() {
        // make sure new indexing is blocked
        haltIndexing = new AtomicBoolean(true);
        
        // remove the locks. this will close the writers and remove the locks
        this.closeWritersAndRemoveLocks();
    }
    
    public void start() {
        this.stop();
        haltIndexing = new AtomicBoolean(false);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    @XmlEnum(String.class)
    public enum IndexPersistType {

        CREATE("Create"),
        UPDATE("Update"),
        DELETE("Delete");

        private String displayName;

        private IndexPersistType(String displayName) {
            this.displayName = displayName;
        }

        public static IndexPersistType getType(String name) {
            for (IndexPersistType tType : IndexPersistType.values()) {
                if (name.equalsIgnoreCase(tType.name()) ||
                        name.equalsIgnoreCase(tType.displayName)) {
                    return tType;
                }
            }

            throw new IllegalArgumentException("Unable to match type: " + name);
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public String getName() {
            return this.name();
        }
    }

    /**
     * Create a new object
     * @param obj
     * @throws IOException
     */
    public void create(TypedObject obj, IndexPathInfo indexPathInfo, boolean isIndexerRequest) throws IndexPersistException {
        this.addDocuments(Arrays.asList(obj), indexPathInfo, IndexPersistType.CREATE, isIndexerRequest);
    }

    /**
     * Create a list of new objects
     * @param objects
     * @param indexType
     * @throws ApiServiceException
     */
    public void createBatch(List<? extends TypedObject> objects, IndexPathInfo indexPathInfo, boolean isIndexerRequest) throws IndexPersistException {
        this.addDocuments(objects, indexPathInfo, IndexPersistType.CREATE, isIndexerRequest);
    }

    /**
     * Update a batch of objects by index type
     * @param objects
     * @param indexType
     * @throws ApiServiceException
     */
    public void updateBatch(List<? extends TypedObject> objects, IndexPathInfo indexPathInfo, boolean isIndexerRequest) throws IndexPersistException {
        this.addDocuments(objects, indexPathInfo, IndexPersistType.UPDATE, isIndexerRequest);
    }

    /**
     * Update an existing object
     * @param obj
     * @throws IOException
     */
    public void update(TypedObject obj, IndexPathInfo indexPathInfo, boolean isIndexerRequest) throws IndexPersistException {
        Long ref = obj.getId();
        if (ref == null) {
            throw new IndexPersistException("Unable to update, missing identity reference.");
        }
        this.addDocuments(Arrays.asList(obj), indexPathInfo, IndexPersistType.UPDATE, isIndexerRequest);
    }

    /**
     * Adds a batch of objects into the index
     * @param obj
     * @throws IOException
     */
    private void addDocuments(List<? extends TypedObject> objects, IndexPathInfo indexPathInfo, IndexPersistType persistType, boolean isIndexerRequest) throws IndexPersistException {
        assert(objects != null);
        
        if (!isIndexerRequest && haltIndexing.get()) {
            throw new IndexPersistException("Indexing is shutting down, please try later.");
        }
        
        synchronized (indexPathInfo.getSyncObject()) {
            IndexWorker indexWorker = indexWorkerMap.get(indexPathInfo.getDbIndexType());
            if ( indexWorker == null ) {
                indexWorker = new IndexWorker();
                indexWorkerMap.put(indexPathInfo.getDbIndexType(), indexWorker);
            }
            indexWorker.indexObjects(objects, indexPathInfo, persistType);
        }
        
    }

    /**
     * Delete a document by reference and index type
     * @param ref
     * @param indexType
     * @throws IOException
     */
    public void delete(IndexPathInfo indexPathInfo, Long reference, boolean isIndexerRequest) throws IndexPersistException {
        
        if (!isIndexerRequest && haltIndexing.get()) {
            throw new IndexPersistException("Indexing is shutting down, please try later.");
        }
        synchronized (indexPathInfo.getSyncObject()) {
            IndexWorker indexWorker = indexWorkerMap.get(indexPathInfo.getDbIndexType());
            if ( indexWorker == null ) {
                indexWorker = new IndexWorker();
                indexWorkerMap.put(indexPathInfo.getDbIndexType(), indexWorker);
            }
            indexWorker.deleteObjects(Arrays.asList(reference), indexPathInfo);
        }
    }
    
    public void delete(IndexPathInfo indexPathInfo, List<Long> references, boolean isIndexerRequest) throws IndexPersistException {
        if (!isIndexerRequest && haltIndexing.get()) {
            throw new IndexPersistException("Indexing is shutting down, please try later.");
        }
        synchronized (indexPathInfo.getSyncObject()) {
            IndexWorker indexWorker = indexWorkerMap.get(indexPathInfo.getDbIndexType());
            if ( indexWorker == null ) {
                indexWorker = new IndexWorker();
                indexWorkerMap.put(indexPathInfo.getDbIndexType(), indexWorker);
            }
            indexWorker.deleteObjects(references, indexPathInfo);
        }
    }
    
    public void closeWritersAndRemoveLocks() {
        
        // close the writer if it's open
        if ( indexWorkerMap != null && !indexWorkerMap.isEmpty() ) {
            for ( DbIndexType indexType : indexWorkerMap.keySet() ) {
                IndexWorker indexWorker = indexWorkerMap.get(indexType);
                
                // create a condition to check if the index worker map size is zero
                Condition condition = new Condition() {
                    @Override
                    public boolean condition() { return !indexWorker.indexing.get(); }
                };
                
                // 3 minute timeout
                long timeoutMs = DateUtils.MILLIS_PER_MINUTE * 3;
                AppUtil.waitUntil(condition, timeoutMs);
                indexWorker.closeIndex();
            }
        }
        
        // go through all directories and remove "write.lock"
        IndexPathInfo luceneBasePath = new IndexPathInfo(AppConstants.LUCENE_STORE_NAME);
        
        File f = new File(FileUtil.getWebPath() + File.separatorChar + luceneBasePath.getFilePath());
        this.deleteLockFiles(f);
    }
    
    public void deleteUnusedUserInstanceDirectories() {
        Set<String> existingCredsInstanceIds = PersistenceManager.getInstance().getExistingSalesforceOauthInstanceIds(true /*lowercaseThem*/);
        
        IndexPathInfo luceneBasePath = new IndexPathInfo(AppConstants.LUCENE_STORE_NAME);
        
        File f = new File(FileUtil.getWebPath() + File.separatorChar + luceneBasePath.getFilePath());
        if ( f.isDirectory() ) {
            // check if this lucene dir has files
            File[] rootFiles = f.listFiles();
            if ( rootFiles != null && rootFiles.length > 0 ) {
                for ( File dirFile : rootFiles ) {
                    
                    if (dirFile.isDirectory()) {
                        String dirName = dirFile.getName().toLowerCase();
                        if ( existingCredsInstanceIds.contains(dirName) ) {
                            continue;
                        }
                        
                        //
                        // It's not found in the existing creds list, check if this has the opportunity dir.
                        // If it has the opportunity dir, delete this directory since it's a tenant dir
                        // and no longer used.
                        //
                        File[] subdirFiles = dirFile.listFiles();
                        boolean deleteDir = false;
                        if ( subdirFiles != null && subdirFiles.length > 0 ) {
                            for ( File subdirFile : subdirFiles ) {
                                if ( subdirFile.isDirectory() && subdirFile.getName().equalsIgnoreCase("opportunity") ) {
                                    // delete this dir
                                    deleteDir = true;
                                    break;
                                }
                            }
                        }
                        if ( deleteDir ) {
                            // we need to delete it recursively
                            boolean deletedDir = FileUtil.deleteDir(dirFile);
                            logger.info("delete unused directory '" + dirName + "' result: " + String.valueOf(deletedDir));
                        }
                    }
                }
            }
        }
    }
    
    private void deleteLockFiles(File f) {
        if (f.isDirectory()) {
            if (f.listFiles() != null) {
                for (File dirFile : f.listFiles()) {
                    if (!dirFile.isDirectory() && dirFile.getName().indexOf(INDEX_WRITE_LOCK) != -1) {
                        dirFile.delete();
                    } else if (dirFile.isDirectory()) {
                        this.deleteLockFiles(dirFile);
                    }
                }
            }
        } else if (f.getName().indexOf(INDEX_WRITE_LOCK) != -1) {
            f.delete();
        }
    }
    
    protected boolean hasLockFile(File f) {
        if (f == null) {
            return false;
        }
        if (f.isDirectory()) {
            if (f.listFiles() != null) {
                for (File dirFile : f.listFiles()) {
                    if (!dirFile.isDirectory() && dirFile.getName().indexOf(INDEX_WRITE_LOCK) != -1) {
                        return true;
                    }
                }
            }
        } else if (f.getName().indexOf(INDEX_WRITE_LOCK) != -1) {
            return true;
        }
        return false;
    }

    //------------------------------------------------------------------------------------
    //
    // Index Worker
    //
    //------------------------------------------------------------------------------------

    protected class IndexWorker {
        
        protected Analyzer analyzer = null;

        public IndexWriter indexWriter = null;
        public AtomicBoolean indexing = new AtomicBoolean(false);
        
        // private constructor to ensure singleton usage
        public IndexWorker() {
            // create the analyzer once
            if ( analyzer == null ) {
                analyzer = new StandardAnalyzer();
            }
        }

        public void deleteObjects(List<Long> referencesToDelete, IndexPathInfo indexPathInfo) throws IndexPersistException {
        	if (referencesToDelete == null) {
        		logger.error("Null references to delete for index type: " + indexPathInfo.getDbIndexType().getIndexId(), null);
        		return;
        	}

            if (referencesToDelete != null) {
            	this.runIndexer(indexPathInfo, null /*objectsToPersist*/, IndexPersistType.DELETE /*persistType*/, referencesToDelete);
            }
        }

        public void indexObjects(List<? extends TypedObject> objects, IndexPathInfo indexPathInfo, IndexPersistType persistType) throws IndexPersistException {
        	if (objects == null) {
        		logger.error("Null objects to index for index type: " + indexPathInfo.getDbIndexType().getIndexId() + " of persist type: " + persistType.getDisplayName(), null);
        		return;
        	}
            
            this.runIndexer(indexPathInfo, objects /*objectsToPersist*/, persistType, null /*referencesToDelete*/);
        }
        
        public void closeIndex() {
            if ( indexWriter != null ) {
                try {
                    //
                    // Close the writer
                    //
                    indexWriter.close();
                    indexWriter = null;
                } catch (Exception e) {
                    if (indexWriter != null) {
                        if (indexWriter.isOpen()) {
                            logger.error("Failed to close the index writer '" + indexWriter.getDirectory().toString() + "'.", e);
                        } else {
                            logger.error("IndexWriter is already closed '" + indexWriter.getDirectory().toString() + "'.", e);
                        }
                    } else {
                        logger.error("Error trying to close the index writer: ", e);
                    }
                }
            }
        }
        
        /**
         * Return the IndexWriter for the specified index name using the directory file.
         * 
         * @param f
         * @param indexTypeName
         * @return IndexWriter
         */
        private IndexWriter getIndexWriterHandler(File f, String indexTypeName) {
            
            //
            // Check if the fs directory has a lock file. If it doesn't that means
            // we have a corrupt index writer and we'll need to obtain a new lock
            //
            boolean foundWriteLock = hasLockFile(f);
            
            if (indexWriter == null) {
                // but check if there's a write lock on it already, if so we need to remove the lock
                if (foundWriteLock) {
                    deleteLockFiles(f);
                }
                
                //
                // It doesn't exist yet, just build it
                //
                try {
                    //
                    // Build the index writer
                    //
                    indexWriter = this.getIndexWriter(f);
                } catch (IOException e) {
                    logger.error("Failed to get the index writer for index '" + indexTypeName + "'.", e);
                }
                return indexWriter;
            }
            
            if (!foundWriteLock) {
                //
                // No lock found
                if (indexWriter.isOpen()) {
                    try {
                        //
                        // Close the IndexWriter since there's no current write lock and it's open.  We need a lock
                        // file present to commit data
                        //
                        indexWriter.close();
                    } catch (IOException e) {
                        logger.error("Failed to close the indexwriter that had no lock '" + indexTypeName + "'.", e);
                    }
                }
                
                try {
                    //
                    // Build the index writer to obtain a new lock
                    //
                    indexWriter = this.getIndexWriter(f);
                } catch (IOException e) {
                    logger.error("Failed to get the index writer for index '" + indexTypeName + "'.", e);
                }
            }
            
            return indexWriter;
        }

        /**
         * TODO: look into whether we should just remove the IndexWriter as an argument since it's not getting used.
         * 
         * @param indexPathInfo
         * @param objects
         * @param persistType
         * @param referencesToDelete
         * @param indexWriter
         * @throws IndexPersistException
         */
        private void runIndexer(
                IndexPathInfo indexPathInfo,
                List<? extends TypedObject> objects,
                IndexPersistType persistType,
                List<Long> referencesToDelete) 
                throws IndexPersistException {
            
            // mark indexing to true
            indexing = new AtomicBoolean(true);
            
        	DbIndexType indexType = indexPathInfo.getDbIndexType();
            long startTime = System.currentTimeMillis();
            
            long avgIndexTime = 0;
            long totalIndexTime = 0;
            long numDocs = 0;
            
            // make sure the directory exists
            File f = FileUtil.getLuceneIndex(indexPathInfo, false /*isSearch*/);
            
            String indexTypeName = indexType.name();
            
            IndexWriter w = null;
            
            // get the index writer
            w = this.getIndexWriterHandler(f, indexTypeName);

            try {

                try {
                    
                    if (persistType == IndexPersistType.CREATE) {
                        // object creation, set the reference id's and index
                        for (TypedObject dto : objects) {
                            this.addDoc(dto, indexType, w);
                        }
                    } else if (persistType == IndexPersistType.UPDATE) {
                        // object deletion, delete the matching doc and re-index
                        for (TypedObject dto : objects) {
                            this.deleteMatchingDoc(dto, w);
                            this.addDoc(dto, indexType, w);
                        }
                    } else {
                        // it's a delete request
                        for (Long ref : referencesToDelete) {
                            Term t = new Term(AppConstants.ID_KEY.toLowerCase(), String.valueOf(ref));
                            w.deleteDocuments(t);
                        }
                    }

                    // commit the index
                    w.commit();

                    // get the current number of docs for this writer
                    numDocs = w.numDocs();

                } catch (Throwable t) {
                    logger.error("Failed to commit document(s) for index '" + indexTypeName + "'.", t);
                    return;
                }
    
                //
                // update the db index info record, but only if the
                // current object is not the db_index_info object
                //
                if ( indexType != DbIndexType.DB_INDEX_INFO_TYPE ) {
    	            if (objects != null) {
    	                totalIndexTime = System.currentTimeMillis() - startTime;
    	                avgIndexTime = totalIndexTime / objects.size();
    	            }
    	
    	            this.updateNumDocs(indexPathInfo, numDocs, avgIndexTime, totalIndexTime);
                }
            } catch (Exception e) {
            	logger.error("Failed to index files for index '" + indexTypeName + "'.", e);
            } finally {
                // mark indexing to false
                indexing = new AtomicBoolean(false);
            }

        }

        /**
         * Update the index db info
         */
        private void updateNumDocs(IndexPathInfo indexPathInfo, long numDocs, long avgIndexTime, long totalIndexTime) {

        	DbIndexType indexType = indexPathInfo.getDbIndexType();
        	
        	//
        	// Create the db_index_info_type path info
        	//
        	IndexPathInfo infoTypePathInfo = new IndexPathInfo(AppConstants.LUCENE_STORE_NAME, DbIndexType.DB_INDEX_INFO_TYPE);
            DbIndexInfo dbInfo = null;
            try {
                dbInfo = (DbIndexInfo) SearchService.getInstance().search(infoTypePathInfo, "indexname", indexType.getIndexId());
            } catch (SearchException e) {
                logger.error("Failed searching for an existing doc by "
                        + "indexname and ID '" + indexType.getIndexId() + "' in index '" 
                        + DbIndexType.DB_INDEX_INFO_TYPE.getCanonicalName() + "'.", e );
            }

            if (dbInfo == null) {
            	//
            	// The db info doesn't yet exist for this index id, create it
                dbInfo = new DbIndexInfo();
                dbInfo.setId(PersistenceManager.getInstance().createId(DbIndexType.DB_INDEX_INFO_TYPE));
                dbInfo.setIndexName(indexType.getIndexId());
                dbInfo.setTenantId(indexPathInfo.getTenantId());
            }

            long totalSpace = FileUtil.getLuceneIndexSize(infoTypePathInfo, false /*isSearch*/);
            dbInfo.setSpaceTotal(totalSpace);
            dbInfo.setDocCount(numDocs);
            dbInfo.setIndexTimeAvg(avgIndexTime);
            dbInfo.setIndexTimeTotal(totalIndexTime);
            dbInfo.setUpdateTime( new DateTime() );

            try {
                PersistenceManager.getInstance().save(dbInfo, true /*isIndexerRequest*/);
            } catch (IndexPersistException e) {
                logger.error("Unable to persist index db info.", e );
            }
        }

        /**
         * Build the IndexWriter by setting up the lucene writer config (create or append)
         *
         * @param f
         * @throws IOException
         */
        private IndexWriter getIndexWriter(File f) throws IOException {
            
            Directory dir = FSDirectory.open(f.toPath());
            
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            
            //
            // optional: for better indexing performance, if you
            // are indexing many documents, increase the RAM
            // buffer.  But if you do this, increase the max heap
            // size to the JVM (eg add -Xmx512m or -Xmx1g):
            //
            iwc.setMaxBufferedDocs( 20 );
            iwc.setRAMBufferSizeMB( 64 );

            IndexWriter w = new IndexWriter(dir, iwc);
            
            // return the index writer
            return w;
        }

        /**
         * Delete matching documents using the object's reference
         * @param objectQueue
         * @param indexId
         * @throws IOException
         * @throws CorruptIndexException
         * @throws FileNotFoundException
         */
        private void deleteMatchingDoc(TypedObject obj, IndexWriter w) throws CorruptIndexException, IOException {
            Term t = new Term(AppConstants.ID_KEY, String.valueOf(obj.getId()));
            if (t != null) {
                w.deleteDocuments(t);
            }
        }

        private void addDoc(TypedObject obj, DbIndexType indexType, IndexWriter w) throws CorruptIndexException, IOException {
            // build the indexable Document based off the bean
            Document doc = this.getObjectDocument(obj, indexType);

            // index it, add the forName to the document
            doc.add(new StringField(AppConstants.FOR_NAME_FIELD, obj.getClass().getCanonicalName(), Field.Store.YES));

            // index the document
            w.addDocument(doc);
        }

        /**
         * Build the object document to index
         * @param obj
         * @return
         */
        private Document getObjectDocument(TypedObject obj, DbIndexType indexType) {

            // get public getter methods
            List<Method> getterMethods = ReflectionUtil.getGetterMethods(obj.getClass());

            Document doc = new Document();
            List<String> tokens = new ArrayList<String>();
            Object fieldValue = null;

            //
            // Use getter methods only. The beans we use to persist 
            // should only provide public getters and private fields.
            //
            for (Method getter : getterMethods) {
                
                Annotation[] annotations = getter.getDeclaredAnnotations();
                // look for ApiDataInfo annotation and if it's isPersisted=false
                if ( annotations != null ) {
                    
                    boolean persistGetterVal = true;
                    for ( Annotation annotation : annotations ) {
                        if ( annotation instanceof ApiDataInfo ) {
                            // if it's "isPersisted=false", this means we're not required
                            // to store this value (it's transient)
                            ApiDataInfo infoAnnotation = ( ApiDataInfo ) annotation;
                            if ( !infoAnnotation.isPersisted() ) {
                                persistGetterVal = false;
                                break;
                            }
                            break;
                        }
                    }
                    if ( !persistGetterVal ) {
                        continue;
                    }
                }
                
                if ( Modifier.isFinal( getter.getModifiers() ) ) {
                    // no need to save final getter vals
                    continue;
                }
                
                // create the getter field name
                String fieldName = ReflectionUtil.getGetterFieldName(getter.getName());

                String fieldStrType = getter.getReturnType().getSimpleName().toLowerCase();

                // don't process the class type
                if (fieldStrType.equals("class")) {
                    continue;
                }

                // check if we can access the value
                int classModifier = obj.getClass().getModifiers();
                if (!Modifier.isAbstract(classModifier) && !Modifier.isInterface(classModifier)) {
                    fieldValue = ReflectionUtil.getGetterMethodValue(getter, obj);
                }

                this.updateDocumentForObject(fieldName, fieldStrType, getter.getReturnType(), fieldValue, doc, tokens, obj.getId());
            }
            
            //
            // SORTING support for ID, and PARENT
            //
            // Add the id and parentRefId
            doc.add(new NumericDocValuesField(AppConstants.ORDERBY_OBJ_ID_REF_KEY, obj.getId()));
            doc.add(new StoredField(AppConstants.ORDERBY_OBJ_ID_REF_KEY, obj.getId()));

            if ( indexType != DbIndexType.DB_INDEX_INFO_TYPE && tokens.size() > 0) {
                // Add the tokenized contents of the object to CONTENTS_FIELD
            	String contentStr = Joiner.on(" ").join(tokens);
                doc.add(new TextField(AppConstants.CONTENTS_FIELD, contentStr, Store.YES));
            }

            return doc;
        }

        /**
         * Build the indexed document from the java object.
         * 
         * Lucene 6.2 allows sorting against the values that are stored,
         * so no need to add another field just for sorting.
         * 
         * @param fieldName (should be lowercased by this point)
         * @param fieldStrType
         * @param returnType
         * @param fieldValue
         * @param doc
         * @param tokens
         */
        private void updateDocumentForObject(
                String fieldName, String fieldStrType, Class<?> returnType, Object fieldValue, Document doc, List<String> tokens, Long id) {
            String str = "";

            // Number values can be filtered with a NumericRangeFilter
            if (fieldValue != null) {
                if ( fieldName.equalsIgnoreCase(AppConstants.ID_KEY) ||
                		fieldName.equalsIgnoreCase(AppConstants.PARENT_ID_KEY) ||
                				fieldName.equalsIgnoreCase(AppConstants.USER_REFERENCE_KEY) ) {
                    // store it as a string
                    doc.add(new StringField(fieldName, String.valueOf(fieldValue), Field.Store.YES));
                } else if (fieldStrType.indexOf("int") == 0) {
                    // integer
                    Integer intValue = ((Integer)fieldValue).intValue();
                    doc.add(new NumericDocValuesField(fieldName, intValue));
                    doc.add(new StoredField(fieldName, intValue));
                } else if (fieldStrType.equals("long")) {
                    // long
                    Long longValue = ((Long)fieldValue).longValue();
                    doc.add(new NumericDocValuesField(fieldName, longValue));
                    doc.add(new StoredField(fieldName, longValue));
                } else if (fieldStrType.equals("float")) {
                    // float
                    Float floatValue = ((Float)fieldValue).floatValue();
                    doc.add(new FloatDocValuesField(fieldName, floatValue));
                    doc.add(new StoredField(fieldName, floatValue));
                } else if (fieldStrType.equals("double")) {
                    // double
                    Double doubleValue = ((Double)fieldValue).doubleValue();
                    doc.add(new DoubleDocValuesField(fieldName, doubleValue));
                    doc.add(new StoredField(fieldName, doubleValue));
                } else if (fieldStrType.equals("boolean")) {
                    // boolean
                    doc.add(new StringField(fieldName, Boolean.toString((Boolean)fieldValue), Field.Store.YES));
                } else if (fieldStrType.equals("short")) {
                    // short
                    doc.add(new StringField(fieldName, Short.toString((Short)fieldValue), Field.Store.YES));
                } else if (returnType.isEnum()) {
                    // enum
                	// str = ((Enum<?>)fieldValue).name();
                    doc.add(new StringField(fieldName, ((Enum<?>)fieldValue).name(), Field.Store.YES));
                } else if (fieldStrType.equals("datetime")) {
                    // org.joda.time.DateTime
                    Long dateValue = ((DateTime)fieldValue).getMillis();
                    doc.add(new NumericDocValuesField(fieldName, dateValue));
                    doc.add(new StoredField(fieldName, dateValue));
                } else if (fieldStrType.equals("date")) {
                    // date
                    Long dateValue = ((Date)fieldValue).getTime();
                    doc.add(new NumericDocValuesField(fieldName, dateValue));
                    doc.add(new StoredField(fieldName, dateValue));
                } else if (fieldStrType.equals("calendar")) {
                    // calendar
                    Long calendarValue = ((Calendar)fieldValue).getTimeInMillis();
                    doc.add(new NumericDocValuesField(fieldName, calendarValue));
                    doc.add(new StoredField(fieldName, calendarValue));
                } else if (fieldStrType.equals("string")) {
                    // string
                    // use a field that is indexed (i.e. searchable), but don't tokenize
                    str = (String) fieldValue;
                    
                    if ( StringUtils.isNotBlank( str ) ) {
                        tokens.add(str);
                        str = str.trim();
                    } else {
                        str = "";
                    }

                    doc.add( new SortedDocValuesField(fieldName, new BytesRef(str)) );
                    doc.add( new StringField(fieldName, str, Field.Store.YES) );
                } else if (fieldStrType.equals("bigdecimal")) {
                    // make it a double
                    double doubleValue = ((BigDecimal)fieldValue).doubleValue();
                    doc.add(new DoubleDocValuesField(fieldName, doubleValue));
                    doc.add(new StoredField(fieldName, doubleValue));
                } else if (fieldStrType.indexOf("jsonarray") != -1) {
                    JsonArray jsonArr = (JsonArray) fieldValue;
                    // stringify it
                    String jsonArrStr = jsonArr.toString();
                    doc.add(new StringField(fieldName, jsonArrStr, Field.Store.YES));
                } else {
                    // try to get a TypedObject sub object and index that in it's own index
                    String canonicalName = returnType.getCanonicalName();
                    try {
                        Class<?> theClass = Class.forName(canonicalName);
                        
                        //
                        // Currently only support persisting a single TypedObject nested within the class
                        // or if the value is in a List or Collection
                        //
                        
                        // check if it's of type TypedObject (single object)
                        if (ReflectionUtil.isClassOfBase(theClass, TypedObject.class)) {
                            //
                            // it's of the right type, lets add it to a map so we can index it once we're done with the current object list
                            //
                            TypedObject innerTypedObject = (TypedObject) theClass.cast(fieldValue);
                            
                            // persist the typed object class
                            Long subObjRef = this.processInnerTypedObject(innerTypedObject, canonicalName, fieldName, doc, id);
                            
                            String refValue = AppConstants.ORDERBY_SUB_OBJ_REF_KEY + "" + subObjRef;
                            doc.add(new StringField(fieldName, refValue, Field.Store.YES));

                        } else if ( theClass.isAssignableFrom( List.class ) || theClass.isAssignableFrom( Collection.class ) ) {
                            //
                            // It's a List or Collection, persist the values in this list
                            //
                            Collection<?> collectionOfObjects = (Collection<?>) theClass.cast(fieldValue);
                            List<String> nonTypedObjectVals = new ArrayList<String>();
                            if ( collectionOfObjects != null && !collectionOfObjects.isEmpty() ) {
                                // get the 1st one to determine if it's a TypedObject or not
                                for ( Object obj : collectionOfObjects ) {
                                    if( obj == null ) {
                                        continue;
                                    }
                                    
                                    if ( !(obj instanceof TypedObject) ) {
                                        // persist as an array of values
                                        nonTypedObjectVals.add( Objects.toString(obj, "") );
                                    } else {
                                        
                                        // create the getter field name
                                        canonicalName = obj.getClass().getCanonicalName();
                                        
                                        // persist the typed object class
                                        this.processInnerTypedObject((TypedObject) obj, canonicalName, fieldName, doc, id);
                                    }
                                }
                            }
                            
                            if ( !nonTypedObjectVals.isEmpty() ) {
                                // persist the comma delimited objects as a string array
                                doc.add(new StringField(fieldName, StringUtils.join(nonTypedObjectVals, ","), Field.Store.YES));
                            } else {
                                //
                                // It's an index type reference object
                                //
                                String refValue = AppConstants.ORDERBY_SUB_OBJ_PARENT_REF_KEY + "" + id;
                                doc.add(new StringField(fieldName, refValue, Field.Store.YES));
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        logger.error("Error trying to obtain the class name while indexing.", e );
                    }
                }
            } else {
                // null value
                doc.add(new StringField(fieldName, str, Field.Store.YES));
            }
        }
        
        /**
         * Index the inner TypedObject.
         *  
         * @param innerTypedObject
         * @param canonicalName
         * @param fieldName
         * @param doc
         * @param parentRefId
         * @return
         */
        private Long processInnerTypedObject( TypedObject innerTypedObject, String canonicalName, String fieldName, Document doc, Long parentRefId ) {
            Long subObjRef = innerTypedObject.getId();
            try {
                
                // set the parent reference ID
                ((TypedObject)innerTypedObject).setParent(parentRefId);
                
                if ( subObjRef == null ) {
                    subObjRef = PersistenceManager.getInstance().create( innerTypedObject, true /*isIndexerRequest*/ );
                } else {
                    PersistenceManager.getInstance().update( innerTypedObject, true /*isIndexerRequest*/ );
                }
                
            } catch (IndexPersistException e) {
                logger.error("Failed to save inner object '" + innerTypedObject.toString() + "'.", e );
            }
            
            return subObjRef;
        }

    } // end IndexWorker thread class
    
    public IndexPathInfo buildIndexPathInfo(TypedObject typedObj) {
        DbIndexType indexType = this.getIndexId(typedObj);
        
        String indexId = indexType.getIndexId();
        IndexPathInfo reqIndexPathInfo = indexPathInfoByIndexId.get(indexId);
        if (reqIndexPathInfo != null ) {
            return reqIndexPathInfo;
        } else {
            reqIndexPathInfo = new IndexPathInfo(AppConstants.LUCENE_STORE_NAME, typedObj.getTenantId(), indexType);
            indexPathInfoByIndexId.put(indexId, reqIndexPathInfo);
        }
        
        return reqIndexPathInfo;
    }
    
    public DbIndexType getIndexId( TypedObject typedObj ) {
        Class<? extends TypedObject> clazz = typedObj.getClass();
        return getIndexTypeByClass(clazz);
    }

    private DbIndexType getIndexTypeByClass(Class<? extends TypedObject> clazz) {
        for ( DbIndexType type : DbIndexType.values() ) {
            if ( type.getCanonicalName().equals(clazz.getCanonicalName() ) ) {
                return type;
            }
        }
        // create a new one on the fly
        DbIndexType unknownType = DbIndexType.GENERIC_INFO_TYPE;
        unknownType.setCanonicalName(clazz.getCanonicalName());
        unknownType.setIndexId(clazz.getSimpleName());
        return unknownType;
    }
    
    public IndexPathInfo buildIndexPathInfo(DbIndexType indexType, String tenantId) {
        return new IndexPathInfo(AppConstants.LUCENE_STORE_NAME, tenantId, indexType);
    }
    
    public void deleteIndex(DbIndexType indexType, String tenantId) {
        IndexPathInfo indexPathInfo = this.buildIndexPathInfo(indexType, tenantId);
        
        synchronized (indexPathInfo.getSyncObject()) {
            // close the writer
            String indexTypeName = indexPathInfo.getDbIndexType().name();
            List<? extends TypedObject> objList= PersistenceManager.getInstance().getAllForIndex(indexPathInfo.getDbIndexType(), indexPathInfo.getTenantId());
            if ( CollectionUtils.isNotEmpty(objList) ) {
                for ( TypedObject obj : objList ) {
                    try {
                        this.delete(indexPathInfo, obj.getId(), false /*isIndexerRequest*/);
                    } catch (Exception e) {
                        logger.trace("Failed to delete index files for index '" + indexTypeName + "'. Error: " + e.toString());
                    }
                }
            }
        }
    }
}
