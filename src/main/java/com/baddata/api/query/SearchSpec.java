/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.lucene.search.SortField.Type;
import org.joda.time.DateTime;

import com.baddata.db.RangeQuery;
import com.baddata.db.SearchQuery;
import com.baddata.db.SortQuery;
import com.baddata.exception.ApiServiceException;
import com.baddata.exception.ApiServiceException.ApiExceptionType;
import com.baddata.exception.BaddataException;
import com.baddata.log.Logger;
import com.baddata.manager.job.JobManager.JobType;
import com.baddata.util.AppConstants;
import com.baddata.util.AppUtil;
import com.baddata.util.DateUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class SearchSpec {

	protected Logger logger = Logger.getLogger(SearchSpec.class.getName());

	private int page; // starting at 1 (default of 1)
	private int limit = AppConstants.MAX_SEARCH_LIMIT;
	private String pattern = null; // generic search against the content field
	private SortQuery sortQuery; // not used by default
	private List<RangeQuery> rangeQueries; // not used by default
	private Long delay = null;
	private List<SearchQuery> queries;
	private boolean fetchAll;

	//
	// These keywords are reserved for the search spec attributes and
	// not used to build a generic search pattern like start, end, or wildcards *
	// To search against the general index content, use the "pattern" attribute.
	// These keywords should also be lowercased as that's how they're checked.
	//
	private final Set<String> reservedQueryKeys = Sets.newHashSet(
			"limit", "page", "min", "max", "ascending", "field", "q", "_",
			"delay", "aggregateby", "toggleby", "tenantusername", "tenantid",
			"reportname", "jobtype", "timeout", "since", "until", "sortby", "sortfield",
			"daterangetype", "sortorder", "paginate", "authtoken", "token",
			"asset", "assetwidth", "assetheight", "_ts",
			"amountfilter", "cyclefilter", "primarygroupfilter");

	//
	// transient variables. these are derived from
	// the search query list on the backend within parseQueryString.
	//
	private DateTime initialStartDate;
	private DateTime startDate;
	private DateTime endDate = new DateTime();
	private DateTime clientStartDate;
	private DateTime clientEndDate = new DateTime();
	private DateTime totalEndDate;
	private DateTime forecastStartDate;
	private DateTime since;
	private DateTime until = new DateTime();
	private long _ts;
	private List<String> aggregationFields;
	private String toggleBy;
	private String tenantId;
	private String asset;
	private int assetWidth;
	private int assetHeight;
	private Long userRef;
	private String reportName;
	private JobType jobType;
	private DateRangeType dateRangeType;
	private String keyMovingQst = "";
	private String keyMovingQet = "";
	private String authToken = "";
	private Set<String> cycleFilters = Sets.newHashSet();
	private Set<String> amountFilters = Sets.newHashSet();
	private Set<String> primaryGroupFilters = Sets.newHashSet();

	// timeout in seconds
	private Long timeout = 0l;

	public SearchSpec() {
		this(false /*getAll*/);
	}

	public SearchSpec(Long userReferenceId) {
		this(false /*getall*/);
		this.userRef = userReferenceId;
	}

	public SearchSpec(boolean getall) {
		queries = new ArrayList<SearchQuery>();
		rangeQueries = new ArrayList<RangeQuery>();
		aggregationFields = new ArrayList<String>();
		toggleBy = "";
		page = 1;
		if ( getall ) {
			this.limit = -1;
		}
		this.fetchAll = getall;
	}

	public enum DateRangeType {
		MONTHLY("monthly", "month"),
		QUARTERLY("quarterly", "quarter"),
		RANGE("range", "range");

		List<String> supportedDateRangeTypeNames = Lists.newArrayList();

		private DateRangeType(String... names) {
			if ( names != null ) {
				for ( String name : names ) {
					supportedDateRangeTypeNames.add(name);
				}
			}
		}

		public static DateRangeType getSupportedDateRangeType(String rangeTypeName) {
			rangeTypeName = (rangeTypeName != null) ? rangeTypeName.trim().toLowerCase() : "";

			for ( DateRangeType rangeTypeEnum : DateRangeType.values() ) {
				if ( rangeTypeEnum.supportedDateRangeTypeNames.contains(rangeTypeName) ) {
					return rangeTypeEnum;
				}
			}
			return null;
		}
	}

	public enum ReportName {
		SF_DATA_QUALITY("sf_dataquality", "quality", "dq", "data_quality"),
		SF_ROLLING_FORECAST("sf_rolling_forecast", "forecast", "rollingforecast"),
		SF_PERFORMANCE("sf_performance", "performers", "contributors", "perf"),
		SF_LINEARITY("sf_linearity", "linearity"),
		SF_COVERAGE("sf_coverage", "conversion", "ratio", "coverage_ratio"),
		SF_COVERAGE_HISTORY("sf_coverage_history", "conversion_history", "ratio_history", "coverage_ratio_history"),
		SF_USER_OBJECTIVES("sf_user_objectives", "objectives", "performance_objective"),
		SF_TREND_STATS("sf_trend_stats", "trend_stats"),
		OPPORTUNITY_DB("opportunity_db", "opportunity"),
		OPPORTUNITY_FIELD_HISTORY_DB("opportunity_field_history_db", "opportunity_field_history", "opportunityfieldhistory"),
		OPPORTUNITY_FIELD_DB("opportunity_field_db", "opportunity_field", "opportunityfield"),
		SF_SUMMARY_DEFAULT("default");

		List<String> supportedReportNames = Lists.newArrayList();

		private ReportName(String... names) {
			if ( names != null ) {
				for ( String name : names ) {
					supportedReportNames.add(name);
				}
			}
		}

		public static ReportName getSupportedReportEnum(String reportName) {
			if (reportName == null) {
				return null;
			}
			ReportName reportNameEnum = ReportName.valueOf(reportName);
			if (reportNameEnum != null) {
				return reportNameEnum;
			}

			reportName = (reportName != null) ? reportName.trim().toLowerCase() : "";

			for ( ReportName rEnum : ReportName.values() ) {
				if ( rEnum.supportedReportNames.contains(reportName) ) {
					return rEnum;
				}
			}
			return null;
		}
	}

	public int getPage() {
		// default page starts at 1
		if ( page == 0 ) {
			page = 1;
		}
		return page;
	}

	public void setPage(int page) {
		if ( page <= 0 ) {
			page = 1;
		}
		this.page = page;
	}

	public long get_ts() {
		return _ts;
	}

	public void set_ts(long _ts) {
		this._ts = _ts;
	}

	public boolean fetchAll() {
		return fetchAll;
	}

	public void setFetchAll(boolean fetchAll) {
		this.fetchAll = fetchAll;
	}

	public void setSortQuery(SortQuery sortQuery) {
		this.sortQuery = sortQuery;
	}

	public SortQuery getSortQuery() {
		return sortQuery;
	}

	public List<RangeQuery> getRangeQueries() {
		return rangeQueries;
	}

	public void setRangeQueries(List<RangeQuery> rangeQueries) {
		this.rangeQueries = rangeQueries;
	}

	public void addRangeQuery(RangeQuery query) {
		if (this.rangeQueries == null) {
			this.rangeQueries = Lists.newArrayList(query);
		} else {
			this.rangeQueries.add(query);
		}
	}

	public JobType getJobType() {
		return jobType;
	}

	public void setJobType(JobType jobType) {
		this.jobType = jobType;
	}

	public DateRangeType getDateRangeType() {
		return dateRangeType;
	}

	public void setDateRangeType(DateRangeType dateRangeType) {
		this.dateRangeType = dateRangeType;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		if ( limit > AppConstants.MAX_SEARCH_LIMIT ) {
			limit = AppConstants.MAX_SEARCH_LIMIT;
		}
		this.limit = limit;
	}

	public String getPattern() {
		return pattern;
	}

	public void setPattern(String pattern) {
		this.pattern = pattern;
	}

	public List<SearchQuery> getQueries() {
		return queries;
	}

	public void setQueries(List<SearchQuery> queries) {
		this.queries = queries;
	}

	public void addQuery(SearchQuery query) {
		if (this.queries == null) {
			this.queries = Lists.newArrayList(query);
		} else {
			this.queries.add(query);
		}
	}

	public Long getDelay() {
		return delay;
	}

	public void setDelay(Long delay) {
		this.delay = delay;
	}

	public List<String> getAggregationFields() {
		return aggregationFields;
	}

	public String getToggleBy() {
		return toggleBy;
	}

	public void setToggleBy(String toggleBy) {
		this.toggleBy = toggleBy;
	}

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public String getAsset() {
		return asset;
	}

	public void setAsset(String asset) {
		this.asset = asset;
	}

	public int getAssetWidth() {
		return assetWidth;
	}

	public void setAssetWidth(int assetWidth) {
		this.assetWidth = assetWidth;
	}

	public int getAssetHeight() {
		return assetHeight;
	}

	public void setAssetHeight(int assetHeight) {
		this.assetHeight = assetHeight;
	}

	public String getAuthToken() {
		return authToken;
	}

	public void setAuthToken(String authToken) {
		this.authToken = authToken;
	}

	public ReportName getReportName() {
		ReportName reportNameEnum = ReportName.getSupportedReportEnum(reportName);
		return reportNameEnum;
	}

	public void setReportName(String reportName) {
		this.reportName = reportName;
	}

	@XmlTransient
	public Long getTimeout() {
		return timeout;
	}

	public void setTimeout(Long timeout) {
		this.timeout = timeout;
	}
	
	@XmlTransient
	public DateTime getInitialStartDate() {
		return initialStartDate;
	}

	public void setInitialStart(DateTime initialStartDate) {
		this.initialStartDate = initialStartDate;
	}

	@XmlTransient
	public DateTime getStartDate() {
		return startDate;
	}

	public void setStartDate(DateTime startDate) {
		this.startDate = startDate;
	}
	
	@XmlTransient
	public DateTime getClientStartDate() {
		return clientStartDate;
	}

	public void setClientStartDate(DateTime clientStartDate) {
		this.clientStartDate = clientStartDate;
	}

	@XmlTransient
	public DateTime getForecastStartDate() {
		return forecastStartDate;
	}

	public void setForecastStartDate(DateTime forecastStartDate) {
		this.forecastStartDate = forecastStartDate;
	}

	@XmlTransient
	public DateTime getEndDate() {
		return endDate;
	}

	public void setEndDate(DateTime endDate) {
		this.endDate = endDate;
	}
	
	@XmlTransient
	public DateTime getClientEndDate() {
		return clientEndDate;
	}

	public void setClientEndDate(DateTime clientEndDate) {
		this.clientEndDate = clientEndDate;
	}

	@XmlTransient
	public DateTime getTotalEndDate() {
		return totalEndDate;
	}

	public void setTotalEndDate(DateTime totalEndDate) {
		this.totalEndDate = totalEndDate;
	}

	@XmlTransient
	public DateTime getSince() {
		return since;
	}

	public void setSince(DateTime since) {
		this.since = since;
	}

	@XmlTransient
	public DateTime getUntil() {
		return until;
	}

	public void setUntil(DateTime until) {
		this.until = until;
	}

	public String getKeyMovingQst() {
		return keyMovingQst;
	}

	public void setKeyMovingQst(String keyMovingQst) {
		this.keyMovingQst = keyMovingQst;
	}

	public String getKeyMovingQet() {
		return keyMovingQet;
	}

	public void setKeyMovingQet(String keyMovingQet) {
		this.keyMovingQet = keyMovingQet;
	}

	public Long getUserRef() {
		return this.userRef;
	}

	public void setUserRef(Long userRef) {
		this.userRef = userRef;
	}

	public void generateTenantId(String tenantUsername) {
		this.tenantId = AppUtil.generateTenantId(tenantUsername, userRef);
	}

	public Set<String> getCycleFilters() {
		return cycleFilters;
	}

	public Set<String> getAmountFilters() {
		return amountFilters;
	}

	public Set<String> getPrimaryGroupFilters() {
		return primaryGroupFilters;
	}

	public void parseQueryString(UriInfo uriInfo) throws ApiServiceException {
		MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();

		List<SearchQuery> searchQueries = new ArrayList<SearchQuery>();

		//
		// vals to build a range query
		String rangeMax = null;
		String rangeMin = null;
		String rangeField = null;

		//
		// vals to build a sort query
		String sortField = null;
		// sort type: Long, String, or Double
		String sortType = null;
		boolean sortAscending = true;

		boolean paginate = false;

		String tenantUsername = null;

		for ( String key : queryParams.keySet() ) {

			List<String> vals = queryParams.get(key);
			if (CollectionUtils.isEmpty(vals) ) {
				continue;
			}

			key = key.trim().toLowerCase();
			for (String val : vals) {
				if (val == null || (val != null && val.trim().equalsIgnoreCase("null"))) {
					// don't set "null" vals, go to the next key/value pair
					continue;
				}

				if ( !reservedQueryKeys.contains(key) ) {

					//
					// Create a SearchQuery for the explicit search field. This
					// will translate to a Occur.MUST lucene query.
					//
					SearchQuery searchQuery = new SearchQuery();

					// set the field to search against
					if (key.equalsIgnoreCase("pattern")) {
						searchQuery.setField(AppConstants.CONTENTS_FIELD);
					} else {
						searchQuery.setField( key );
					}

					//
					// Set the start and/or end datetime if it's passed in
					//
					if ( key.equals("start") ) {
						try {
							clientStartDate = new DateTime(val);
							startDate = DateUtil.buildUtcDateTime(val);
						} catch (BaddataException e) {
							throw new ApiServiceException("Invalid 'start' query string value '" + val + "'. Please use an ISO 8601 format (i.e. 2017-01-17T09:49:17.975-08:00)", ApiExceptionType.BAD_REQUEST);
						}
					} else if ( key.equals("end") ) {
						try {
							clientEndDate = new DateTime(val);
							endDate = DateUtil.buildUtcDateTime(val);
						} catch (BaddataException e) {
							throw new ApiServiceException("Invalid 'end' query string value '" + val + "'. Please use an ISO 8601 format (i.e. 2017-01-17T09:49:17.975-08:00)", ApiExceptionType.BAD_REQUEST);
						}
					} else {
						//
						// add a wildcard if it doesn't have it after each token and quotes are not around it all
						//
						int endIdx = val.length() - 1;
						if ((val.charAt(0) == '\'' || val.charAt(0) == '"') && (val.charAt(endIdx) == '\'' || val.charAt(endIdx) == '"')) {
							val = val.trim();
						} else {
							val = val + "*";
						}
					}
					// set the pattern value ( the keyword query )
					searchQuery.setPattern( val );
					searchQueries.add(searchQuery);
				} else if ( key.equals("authtoken") || key.equals("token") ) {
					this.setAuthToken(val);
				} else if ( key.equals( "paginate" ) ) {
					paginate = Boolean.valueOf(val);
				} else if ( key.equals("limit") ) {
					this.limit = (Strings.isNullOrEmpty( val )) ? AppConstants.MAX_SEARCH_LIMIT : Integer.valueOf( val );
				} else if ( key.equals("page") ) {
					this.page = (Strings.isNullOrEmpty( val )) ? 1 : Integer.valueOf( val);
					// use fetch all if it's page 2 or greater
					if (this.page > 1) {
						this.fetchAll = true;
					}
				} else if ( key.equals("timeout") ) {
					if ( NumberUtils.isNumber(val) ) {
						long longVal = Long.valueOf( val );
						if ( longVal > (60 * 5) ) {
							// max is 5 minutes
							this.timeout = (long) (60 * 5);
						} else {
							this.timeout = longVal;
						}
					} else {
						throw new ApiServiceException("Invalid 'timeout' query string value '" + timeout + "'. Please use a number value (in seconds).", ApiExceptionType.BAD_REQUEST);
					}
				} else if ( key.equals("since") ) {
					try {
						this.since = DateUtil.buildUtcDateTime(val);
					} catch (BaddataException e) {
						throw new ApiServiceException("Invalid 'since' query string value '" + val + "'. Please use an ISO 8601 format (i.e. 2017-01-17T09:49:17.975-08:00)", ApiExceptionType.BAD_REQUEST);
					}
				} else if ( key.equals("until") ) {
					try {
						this.until = DateUtil.buildUtcDateTime(val);
					} catch (BaddataException e) {
						throw new ApiServiceException("Invalid 'until' query string value '" + val + "'. Please use an ISO 8601 format (i.e. 2017-01-17T09:49:17.975-08:00)", ApiExceptionType.BAD_REQUEST);
					}
				} else if ( key.equals("min") ) {
					rangeMin = val;
				} else if ( key.equals("max") ) {
					rangeMax = val;
				} else if ( key.equals("rangefield") ) {
					rangeField = val;
				} else if ( key.equals("sortfield") || key.equals("sortby") ) {
					sortField = val;
				} else if ( key.equals("sorttype") ) {
					sortType = val;
				} else if ( key.equals("sortorder") ) {
					if ( StringUtils.isNotBlank(val) ) {
						val = val.toLowerCase();
						if (val.indexOf("desc") != -1) {
							sortAscending = false;
						}
					}
				} else if ( key.equals("delay") ) {
					this.delay = Long.valueOf( val );
				} else if ( key.equals("aggregateby") ) {
					this.getAggregationFields().add(val);
				} else if ( key.equals("toggleby") ) {
					this.setToggleBy(val);
				} else if ( key.equals("tenantid") ) {
					this.setTenantId(val);
				} else if ( key.equals("tenantusername") ) {
					tenantUsername = val;
					//
					// create the tenant Id if the tenantUsername was provided
					this.generateTenantId(tenantUsername);
				} else if ( key.equals("jobtype") ) {
					JobType jobType = JobType.getJobType(val);
					this.setJobType(jobType);
				} else if ( key.equals("daterangetype") ) {
					// dateRangeType
					DateRangeType rangeType = DateRangeType.getSupportedDateRangeType(val);
					this.setDateRangeType(rangeType);
				} else if ( key.equals("reportname") ) {
					ReportName supportedReportEnum = ReportName.getSupportedReportEnum(val);
					if ( supportedReportEnum == null ) {
						// throw an api service exception that this report name is not supported
						throw new ApiServiceException("Report name argument '" + val + "' is not supported.", ApiExceptionType.VALIDATION_ERROR);
					}
					this.setReportName(val);
				} else if ( key.equals("amountfilter") ) {
					this.getAmountFilters().add(val);
				} else if ( key.equals("cyclefilter") ) {
					this.getCycleFilters().add(val);
				} else if ( key.equals("primarygroupfilter") ) {
					this.getPrimaryGroupFilters().add(val);
				} else if ( key.equals("asset") ) {
					this.setAsset(val);
				} else if ( key.equals("assetwidth") ) {
					try {
						this.setAssetWidth(Integer.valueOf(val));
					} catch (Exception e) {
						//
					}
				} else if ( key.equals("assetheight") ) {
					try {
						this.setAssetHeight(Integer.valueOf(val));
					} catch (Exception e) {
						//
					}
				} else if ( key.equals("_ts") ) {
					// get the timestamp value
					try {
						this.set_ts(Long.valueOf(val));
					} catch (Exception e) {
						//
					}
				}
			}
		}

		if (paginate && this.getPage() > 1 && this.limit > 0) {
			this.fetchAll = true;
		}

		if ( StringUtils.isNotBlank(rangeField) ) {
			boolean hasMin = ( StringUtils.isNotBlank(rangeMin) );
			boolean hasMax = ( StringUtils.isNotBlank(rangeMax) );

			if ( !hasMax && !hasMin ) {
				//
				// A min and max is required, throw an exception
				// TODO: throw an API exception
			}

			//
			// Build the range query
			boolean isLongRange = true;
			// prove the min or max are not long values
			try {
				if ( hasMin ) {
					Long.parseLong(rangeMin);
				}
				if ( hasMin ) {
					Long.parseLong(rangeMin);
				}
			} catch (Exception e) {
				isLongRange = false;
			}
			RangeQuery rq = new RangeQuery(rangeField, rangeMin, rangeMax, isLongRange);
			this.setRangeQueries(new ArrayList<RangeQuery>(Arrays.asList(rq)));
		}

		if ( StringUtils.isNotBlank(sortField) ) {
			//
			// Build the sort query
			Type dbSortType = Type.STRING;
			if ( StringUtils.isNotBlank(sortType) ) {
				sortType = sortType.trim().toLowerCase();
				if ( sortType.indexOf("long") != -1 || sortType.indexOf("number") != -1) {
					dbSortType = Type.LONG;
				} else if ( sortType.indexOf("double") != -1 ) {
					dbSortType = Type.DOUBLE;
				}
			}
			SortQuery sq = new SortQuery();
			sq.setField(sortField);
			sq.setIsAscending(sortAscending);
			sq.setSortType(dbSortType);
			sq.setIsAscending(sortAscending);
			this.sortQuery = sq;
		}

		this.queries = searchQueries;
	}

	public String toQueryString() {
		return this.toQueryString(true /*addQuestionMark*/);
	}

	public String toQueryString(boolean addQuestionMark) {
		List<String> qryList = new ArrayList<String>();

		String pageNum = ( page > 0 ) ? "" + page : "1";
		qryList.add( "page=" + pageNum );
		qryList.add( "limit=" + limit );
		if ( sortQuery != null ) {
			if ( StringUtils.isNotBlank(sortQuery.getField()) ) {
				qryList.add("sortField="+sortQuery.getField());
			}
			if ( sortQuery.getSortType() != null ) {
				if ( sortQuery.getSortType() == Type.STRING ) {
					qryList.add("sortType=string");
				} else if ( sortQuery.getSortType() == Type.DOUBLE ) {
					qryList.add("sortType=double");
				} else {
					qryList.add("sortType=number");
				}
			}
		}

		if ( CollectionUtils.isNotEmpty(rangeQueries) ) {
			for ( RangeQuery rangeQuery : rangeQueries ) {
				if ( StringUtils.isNotBlank(rangeQuery.getField()) ) {
					qryList.add("rangeField="+rangeQuery.getField());
				}
				if ( StringUtils.isNotBlank(rangeQuery.getMax()) ) {
					qryList.add("max="+rangeQuery.getMax());
				}
				if ( StringUtils.isNotBlank(rangeQuery.getMin()) ) {
					qryList.add("min="+rangeQuery.getMin());
				}
			}
		}
		String qryStr = Joiner.on("&").join(qryList);
		return (addQuestionMark) ? "?" + qryStr : qryStr;
	}

	public MultivaluedMap<String, String> toMultivaluedMap() {
		MultivaluedMap<String, String> params = new MultivaluedMapImpl();

		String pageStr = ( page > 0 ) ? "" + page : "1";
		params.add( "page", pageStr );

		String limitStr = ( limit > 0 ) ? "" + limit : "" + AppConstants.MAX_SEARCH_LIMIT;
		params.add( "limit", limitStr );

		if ( sortQuery != null ) {
			if ( StringUtils.isNotBlank(sortQuery.getField()) ) {
				params.add("sortField", sortQuery.getField());
			}
			if ( sortQuery.getSortType() != null ) {
				if ( sortQuery.getSortType() == Type.STRING ) {
					params.add("sortType", "string");
				} else if ( sortQuery.getSortType() == Type.DOUBLE ) {
					params.add("sortType", "double");
				} else {
					params.add("sortType", "number");
				}
			}
		}

		if ( CollectionUtils.isNotEmpty(rangeQueries) ) {
			for ( RangeQuery rangeQuery : rangeQueries ) {
				if ( StringUtils.isNotBlank(rangeQuery.getField()) ) {
					params.add("rangeField", rangeQuery.getField());
				}
				if ( StringUtils.isNotBlank(rangeQuery.getMax()) ) {
					params.add("max", rangeQuery.getMax());
				}
				if ( StringUtils.isNotBlank(rangeQuery.getMin()) ) {
					params.add("min", rangeQuery.getMin());
				}
			}
		}

		if ( CollectionUtils.isNotEmpty( queries ) ) {
			for ( SearchQuery query : queries ) {
				if ( StringUtils.isNotBlank( query.getField() ) ) {
					params.add( "pattern", query.getPattern() );
				}
			}
		} else if ( StringUtils.isNoneBlank( pattern ) ) {
			params.add( "pattern", pattern );
		}

		return params;
	}

	@Override
	public String toString() {
		return "SearchSpec [page=" + page + ", limit=" + limit + ", sortQuery=" + sortQuery + ", rangeQueries="
				+ rangeQueries + ", delay=" + delay + ", queries=" + queries + ", fetchAll=" + fetchAll
				+ ", reservedQueryKeys=" + reservedQueryKeys + ", start=" + startDate + ", end=" + endDate + ", since=" + since
				+ ", until=" + until + ", aggregationFields=" + aggregationFields + ", toggleBy=" + toggleBy
				+ ", tenantId=" + tenantId + ", userRef=" + userRef + ", reportName=" + reportName + ", jobType="
				+ jobType + ", timeout=" + timeout + "]";
	}


}
