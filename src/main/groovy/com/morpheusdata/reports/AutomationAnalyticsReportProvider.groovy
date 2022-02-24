package com.morpheusdata.reports

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportType
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.model.ContentSecurityPolicy
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.response.ServiceResponse
import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import io.reactivex.Observable;
import java.util.Date
import java.util.concurrent.TimeUnit
import groovy.json.*

import java.sql.Connection

@Slf4j
class AutomationAnalyticsReportProvider extends AbstractReportProvider {
	Plugin plugin
	MorpheusContext morpheusContext

	AutomationAnalyticsReportProvider(Plugin plugin, MorpheusContext context) {
		this.plugin = plugin
		this.morpheusContext = context
	}

	@Override
	MorpheusContext getMorpheus() {
		morpheusContext
	}

	@Override
	Plugin getPlugin() {
		plugin
	}

	@Override
	String getCode() {
		'automation-analytics-report'
	}

	@Override
	String getName() {
		'Automation Analytics'
	}

	 ServiceResponse validateOptions(Map opts) {
		 return ServiceResponse.success()
	 }


	@Override
	HTMLResponse renderTemplate(ReportResult reportResult, Map<String, List<ReportResultRow>> reportRowsBySection) {
		ViewModel<String> model = new ViewModel<String>()
		def HashMap<String, String> reportPayload = new HashMap<String, String>();
		def webnonce = morpheus.getWebRequest().getNonceToken()
		reportPayload.put("webnonce",webnonce)
		reportPayload.put("reportdata",reportRowsBySection)
		model.object = reportPayload
		getRenderer().renderTemplate("hbs/automationAnalyticsReport", model)
	}

	/**
	 * Allows various sources used in the template to be loaded
	 * @return
	 */
	@Override
	ContentSecurityPolicy getContentSecurityPolicy() {
		def csp = new ContentSecurityPolicy()
		csp
	}


	void process(ReportResult reportResult) {
		// Update the status of the report (generating) - https://developer.morpheusdata.com/api/com/morpheusdata/model/ReportResult.Status.html
		morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.generating).blockingGet();
		Long displayOrder = 0
		List<GroovyRowResult> results = []
		List<GroovyRowResult> tasks = []
		List<GroovyRowResult> taskTypes = []
		Connection dbConnection
		Long successfulJobs = 0
		Long failedJobs = 0
		Long runningJobs = 0
		Long queuedJobs = 0
		Long totalJobs = 0

		Long ansibleJobs = 0
		Long ansibleTowerJobs = 0
		Long chefBootstrapJobs = 0
		Long emailJobs = 0
		Long libraryScriptJobs = 0
		Long libraryTemplateJobs = 0
		Long groovyJobs = 0 
		Long javascriptJobs = 0
		Long jRubyJobs = 0
		Long httpJobs = 0
		Long pythonJobs  = 0
		Long powerShellJobs = 0
		Long puppetAgentInstallJobs = 0
		Long restartJobs = 0
		Long shellScriptJobs = 0
		Long otherJobs = 0
		Long vROJobs = 0

		String taskType
		List<GroovyRowResult> users = []
		List<GroovyRowResult> taskTemplates = []
		def emptyMap = [:]

		String phraseMatch = "${reportResult.configMap?.duration}%"
		
		// Get the current date
		def today = new Date()
		println(today)

		// Get the dates from the previous x days
		String daysDuration = "${reportResult.configMap?.duration}"
		int previousDays = daysDuration.toInteger() + 1
		def daysRange = []
		for(int i = 0;i<previousDays;i++) {
			def datadate = today - i
			daysRange << datadate.format("yyyy-MM-dd")
		}
		// Store date data
		println(daysRange)
		def dataMap = [:]
		daysRange.each{
			println it
			def stateData = [successful: 0, failed: 0, queued: 0, running: 0]
			dataMap[it] = stateData
		}

		try {
			// Create a read-only database connection
			dbConnection = morpheus.report.getReadOnlyDatabaseConnection().blockingGet()
			tasks = new Sql(dbConnection).rows("SELECT id,name,code,task_type_id from task order by id asc;")
			taskTypes = new Sql(dbConnection).rows("SELECT id,code,name from task_type order by id asc;")
			results = new Sql(dbConnection).rows("SELECT job_template_name,start_date,end_date,duration,status,created_by_id,job_template_id from morpheus.job_execution WHERE created_by_id IS NOT NULL AND (start_date BETWEEN (CURDATE() - INTERVAL ${phraseMatch} DAY) AND CURDATE()) order by start_date desc;")
			//results = new Sql(dbConnection).rows("SELECT job_template_name,start_date,end_date,duration,status,created_by_id,job_template_id from morpheus.job_execution WHERE created_by_id IS NOT NULL AND (start_date BETWEEN '2022/01/01' AND '2022/01/31') order by start_date desc;")
			taskTemplates = new Sql(dbConnection).rows("SELECT id,schedule_mode,ref_type from job_template order by id asc;")
			users = new Sql(dbConnection).rows("SELECT id,username from user order by id asc;")
		} finally {
	        // Close the database connection
			morpheus.report.releaseDatabaseConnection(dbConnection)
		}
		Observable<GroovyRowResult> observable = Observable.fromIterable(results) as Observable<GroovyRowResult>
		observable.map{ resultRow ->
			log.info("Mapping resultRow ${resultRow}")
			println "Mapping resultRow ${resultRow}"

			// Parse date
			def dbdate = resultRow.start_date.toString()
			def splitData = dbdate.split(" ")
			println splitData

			String format
			Boolean isRunning
			Boolean isSuccessful
			Boolean isFailed
			Boolean isQueued
			Boolean isAnsibleTask 
			Boolean isPythonTask
			Boolean isShellScriptTask
			Boolean isPowerShellTask
			Boolean isNotFoundTask
			String nameId

			isRunning = false
			isSuccessful = false
			isFailed = false
			isQueued = false

			if (resultRow.status == "running"){
				isRunning = true
				dataMap[splitData[0]]["running"]++
				println(dataMap)
			}

			if (resultRow.status == "success"){
				isSuccessful = true
				dataMap[splitData[0]]["successful"]++
				println(dataMap)
			}

			if (resultRow.status == "queued"){
				isQueued = true
				dataMap[splitData[0]]["queued"]++
				println(dataMap)
			}

			if (resultRow.status == "error"){
				dataMap[splitData[0]]["failed"]++
				println(dataMap)
				isFailed = true
			}

			// Ensure that the task is not running or queued
			//if (resultRow.status != "running" || resultRow.status != "queued" || resultRow.duration != null){
			if(resultRow.duration != null){
				long milliseconds = resultRow.duration
				long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % TimeUnit.HOURS.toMinutes(1);
				long seconds = TimeUnit.MILLISECONDS.toSeconds(resultRow.duration) % TimeUnit.MINUTES.toSeconds(1);
				format = String.format("%02d min %02d seconds", Math.abs(minutes), Math.abs(seconds));
			}
			else {
				format = resultRow.duration
			}

			String username
		    users.each { 
				if (resultRow.created_by_id == it.id) {
					username = it.username
				}
			}

			totalJobs++
			if (resultRow.status.startsWith('error')) {
				failedJobs++
			}
			if (resultRow.status.startsWith('success')) {
				successfulJobs++
			}
			if (resultRow.status.startsWith('running')) {
				runningJobs++
			}
			if (resultRow.status.startsWith('queued')) {
				queuedJobs++
			}

			String automationType
			taskTemplates.each {
				if (resultRow.job_template_id == it.id){
					if (it.ref_type == "task"){
						automationType = "task"
					} else {
						automationType = "workflow"
					}
				}
			}

			emptyMap[resultRow.job_template_name] = "demo"
			tasks.each { 
				if (resultRow.job_template_name == it.name) {
					println it
					def taskId = it.task_type_id
					nameId = it.id
					taskTypes.each {
						if (taskId == it.id){
							println it.name
							taskType = it.name
						}
					}
				}
			}

			switch(taskType) {
				case "Ansible Playbook":
					isAnsibleTask = true
					ansibleJobs++
					break;
				case "Ansible Tower Job":
					ansibleTowerJobs++
					break;
				case "Chef bootstrap":
					chefBootstrapJobs++
					break;
				case "Email":
					emailJobs++
					break;
				case "Groovy Script":
					groovyJobs++
					break;
				case "HTTP":
					httpJobs++
					break;
				case "Javascript":
					javascriptJobs++
					break;
				case "jRuby Script":
					jRubyJobs++
					break;
				case "Library Script":
					libraryScriptJobs++
					break;
				case "Library Template":
					libraryTemplateJobs++
					break;
				case "Powershell Script":
					isPowerShellTask = true
					powerShellJobs++
					break;
				case "Puppet Agent Install":
					puppetAgentInstallJobs++
					break;
				case "Python Script":
					isPythonTask = true
					pythonJobs++
					break;
				case "Restart":
					restartJobs++
					break;
				case "Shell Script":
					isShellScriptTask = true
					shellScriptJobs++
					break;
				case "vRealize Orchestrator Workflow":
					vROJobs++
					break;
				default:
				    println "Task with no icon found"
				    isNotFoundTask = true
			}

			Map<String,Object> data = [name: resultRow.job_template_name, task_id: nameId, start_date: resultRow.start_date.toString(), end_date: resultRow.end_date.toString(), 
									   duration: format, status: resultRow.status, 
									   created_by: username, is_running: isRunning, is_successful: isSuccessful, is_failed: isFailed, is_queued: isQueued,
									   isAnsibleTask: isAnsibleTask, isPythonTask: isPythonTask, isShellScriptTask: isShellScriptTask,
									   isNotFoundTask: isNotFoundTask, isPowerShellTask: isPowerShellTask, automationType: automationType]
			ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
			log.info("resultRowRecord: ${resultRowRecord.dump()}")

			return resultRowRecord
		}.buffer(50).doOnComplete {
			morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.ready).blockingGet();
		}.doOnError { Throwable t ->
			morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.failed).blockingGet();
		}.subscribe {resultRows ->
			morpheus.report.appendResultRows(reportResult,resultRows).blockingGet()
		}
		def dataOut = []
		dataMap.each{
     		def jsonMap = [:]
			jsonMap["date"] = it.key
			jsonMap["data"] = it.value
			dataOut << jsonMap
		}

		def json = JsonOutput.toJson(dataOut)
		println("JSON Payload: ${json}")
		Map<String,Object> data = [total_jobs: totalJobs, successful_jobs: successfulJobs, failed_jobs: failedJobs, running_jobs: runningJobs, queued_jobs: queuedJobs,
								   timeDuration: reportResult.configMap?.duration, pythonJobs: pythonJobs, ansibleJobs: ansibleJobs,
								   powerShellJobs: powerShellJobs, shellScriptJobs: shellScriptJobs, json_payload: json]
		ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_HEADER, displayOrder: displayOrder++, dataMap: data)
        morpheus.report.appendResultRows(reportResult,[resultRowRecord]).blockingGet()
		println emptyMap
	}

	// https://developer.morpheusdata.com/api/com/morpheusdata/core/ReportProvider.html#method.summary
	// The description associated with the custom report
	 @Override
	 String getDescription() {
		 return "Automation analytics"
	 }

	// The category of the custom report
	 @Override
	 String getCategory() {
		 return 'inventory'
	 }

	 @Override
	 Boolean getOwnerOnly() {
		 return false
	 }

	 @Override
	 Boolean getMasterOnly() {
		 return true
	 }

	 @Override
	 Boolean getSupportsAllZoneTypes() {
		 return true
	 }

	// https://developer.morpheusdata.com/api/com/morpheusdata/model/OptionType.html
	 @Override
	 List<OptionType> getOptionTypes() {
		  [new OptionType(code: 'automation-report-duration', name: 'Duration', 	inputType: 'SELECT', optionSource: 'durationOptions', fieldName: 'duration', fieldContext: 'config', fieldLabel: 'Duration', displayOrder: 0)]
	 }
 }