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
class AutomationOverviewReportProvider extends AbstractReportProvider {
	Plugin plugin
	MorpheusContext morpheusContext

	AutomationOverviewReportProvider(Plugin plugin, MorpheusContext context) {
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
		'automation-overview-report'
	}

	@Override
	String getName() {
		'Automation Overview'
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
		getRenderer().renderTemplate("hbs/automationOverviewReport", model)
	}


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
		Long totalTasks = 0
		Long ansibleTasks = 0
		Long ansibleTowerTasks = 0
		Long chefBootstrapTasks = 0
		Long emailTasks = 0
		Long libraryScriptTasks = 0
		Long libraryTemplateTasks = 0
		Long groovyTasks = 0 
		Long javascriptTasks = 0
		Long jRubyTasks = 0
		Long httpTasks = 0
		Long pythonTasks  = 0
		Long powerShellTasks = 0
		Long puppetAgentInstallTasks = 0
		Long restartTasks = 0
		Long shellScriptTasks = 0
		Long otherTasks = 0
		Long vROTasks = 0
		String taskType
		List<GroovyRowResult> taskTemplates = []
		def emptyMap = [:]

		try {
			// Create a read-only database connection
			dbConnection = morpheus.report.getReadOnlyDatabaseConnection().blockingGet()
			// Evaluate if a search filter or phrase has been defined
			results = new Sql(dbConnection).rows("SELECT id,name,code,task_type_id from task order by id asc;")
			taskTypes = new Sql(dbConnection).rows("SELECT id,code,name from task_type order by id asc;")
			String phraseMatch = "${reportResult.configMap?.duration}%"
			//results = new Sql(dbConnection).rows("SELECT job_template_name,start_date,end_date,duration,status,created_by_id,job_template_id from morpheus.job_execution WHERE created_by_id IS NOT NULL AND (start_date BETWEEN (CURDATE() - INTERVAL ${phraseMatch} DAY) AND CURDATE()) order by start_date desc;")
			//results = new Sql(dbConnection).rows("SELECT job_template_name,start_date,end_date,duration,status,created_by_id,job_template_id from morpheus.job_execution WHERE created_by_id IS NOT NULL AND (start_date BETWEEN '2022/01/01' AND '2022/01/31') order by start_date desc;")
			taskTemplates = new Sql(dbConnection).rows("SELECT id,schedule_mode,ref_type from job_template order by id asc;")
	    // Close the database connection
		} finally {
			morpheus.report.releaseDatabaseConnection(dbConnection)
		}
		Observable<GroovyRowResult> observable = Observable.fromIterable(results) as Observable<GroovyRowResult>
		observable.map{ resultRow ->
			log.info("Mapping resultRow ${resultRow}")
			println "Mapping resultRow ${resultRow}"
			totalTasks++

			Boolean isNotFoundTask

			def taskId = resultRow.task_type_id
			taskTypes.each {
				if (taskId == it.id){
					taskType = it.name
				}
			}

			switch(taskType) {
				case "Ansible Playbook":
					ansibleTasks++
					break;
				case "Ansible Tower Job":
					ansibleTowerTasks++
					break;
				case "Chef bootstrap":
					chefBootstrapTasks++
					break;
				case "Email":
					emailTasks++
					break;
				case "Groovy Script":
					groovyTasks++
					break;
				case "HTTP":
					httpTasks++
					break;
				case "Javascript":
					javascriptTasks++
					break;
				case "jRuby Script":
					jRubyTasks++
					break;
				case "Library Script":
					libraryScriptTasks++
					break;
				case "Library Template":
					libraryTemplateTasks++
					break;
				case "Powershell Script":
					powerShellTasks++
					break;
				case "Puppet Agent Install":
					puppetAgentInstallTasks++
					break;
				case "Python Script":
					pythonTasks++
					break;
				case "Restart":
					restartTasks++
					break;
				case "Shell Script":
					shellScriptTasks++
					break;
				case "vRealize Orchestrator Workflow":
					vROTasks++
					break;
				default:
				    otherTasks++
			}

			Map<String,Object> data = [name: resultRow.name]
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
		Map<String,Object> orderedTasks = ["Ansible Tasks": ansibleTasks,"Ansible Tower Tasks": ansibleTowerTasks, "Chef Bootstrap Tasks": chefBootstrapTasks,
		                                   "Email Tasks": emailTasks, "Groovy Tasks": groovyTasks, "HTTP Tasks": httpTasks, "JavaScript Tasks": javascriptTasks,
                                           "JRuby Tasks": jRubyTasks, "Library Script Tasks": libraryScriptTasks, "Library Template Tasks": libraryTemplateTasks, "Puppet Agent Install": puppetAgentInstallTasks, "Python Tasks": pythonTasks, "Restart Tasks": restartTasks, 
								           "PowerShell Tasks": powerShellTasks, "Shell Script Tasks": shellScriptTasks, "vRealize Orchestrator Tasks": vROTasks]
		Map<String,Object> maporder = orderedTasks.sort {-it.value}
		//println "ordered map: ${maporder}"
		def Map<String,Object> topFiveTasks = [:]

		def outdemo = []
		maporder.eachWithIndex { key, val, index ->
		    if (index < 5) {
				topFiveTasks.put(key, val)
			}
			def g = [:]
			g["name"] = key
			g["count"] = val
			switch(key) {
				case "Ansible Tasks":
					g["imagesrc"] = "/assets/plugin/automation-insights/ansible-7c5dd415cf67a493bad514210c7e859f.svg"
					break;
				case "Ansible Tower Tasks":
					g["imagesrc"] = "/assets/containers-row-png/ansibleTower@2x-91449d8232a3d2a6012d905995a2beda.png"
					break;
				case "Chef Bootstrap Tasks":
				    g["imagesrc"] = "/assets/containers-row-png/chef@2x-2c63ec93466882be21961755cf80d6f1.png"
					break;
				case "Email Tasks":
				    g["imagesrc"] = "/assets/containers-row-png/email@2x-d45b30cdbf8ca3762b9052e57fab42d5.png"
					break;
				case "Groovy Tasks":
				    g["imagesrc"] = "/assets/containers-row-png/groovy@2x-79e9c5642b76cb9d066919c0e5456ad1.png"
					break;
				case "HTTP Tasks":
				    g["imagesrc"] = "/assets/containers-row-png/http@2x-ebefb9f3bcfabee04b42d1b734c834f7.png"
					break;
				case "JavaScript Tasks":
				    g["imagesrc"] = "/assets/plugin/automation-insights/javascript-b0a7b5fedfe491b4113773b32f4c35eb.svg"
					break;
				case "JRuby Tasks":
				    g["imagesrc"] = "/assets/plugin/automation-insights/jruby-e3c1214468cf650c04251415d10bbd18.svg"
					break;
				case "Library Script Tasks":
					g["imagesrc"] = "/assets/plugin/automation-insights/containerScript-64dccfd320266e7e05db23ad8e4b9163.svg"
					break;
				case "Library Template Tasks":
				    g["imagesrc"] = "/assets/containers-row-png/containerTemplate@2x-6d3583c81bcd526604572ba669064b95.png"
					break;
				case "PowerShell Tasks":
				    g["imagesrc"] = "/assets/containers-row-png/winrm@2x-2d211656e205b030f2127368151052e9.png"
					break;
				case "Puppet Agent Install":
				    g["imagesrc"] = "/assets/containers-row-png/puppet@2x-1b56bea9f233b58f369416ad66bc9ce2.png"
					break;
				case "Python Tasks":
					g["imagesrc"] = "/assets/plugin/automation-insights/jython-edfe385e8fb29643380d7437c4ee92fc.svg"
					break;
				case "Restart Tasks":
				    g["imagesrc"] = "/assets/containers-row-png/restart@2x-912f62cd1efa0ad852d9e1621f2f13ca.png"
					break;
				case "Shell Script Tasks":
					g["imagesrc"] = "/assets/plugin/automation-insights/script-152184e936fc34c09825ca9b917e93b5.svg"
					break;
				case "vRealize Orchestrator Tasks":
					g["imagesrc"] = "/assets/plugin/automation-insights/vro-251a797f27b46f0686f7017110654295.svg"
					break;
				default:
					break;
			}
			outdemo.add(g)
		}

		//println "outdemo ${outdemo}"
		def list = []
		def topFiveCount = 0
		def String outColor
		topFiveTasks.eachWithIndex { key, val, index -> 
		    if (index == 0){
				outColor = "#3366cc"
			}
			if (index == 1){
				outColor = "#dc3912"
			}
			if (index == 2){
				outColor = "#ff9900"
			}
			if (index == 3){
				outColor = "#109618"
			}
			if (index == 4){
				outColor = "#990099"
			}
			def output = [name: key, value: val, color: outColor]
			topFiveCount = topFiveCount + val
      		//println(output);  
			list << output
		}
		//println("Top Five count: ${topFiveCount}")
		def otherCount = [name: "Others", value: totalTasks - topFiveCount, color: "#0099c6"]
		list << otherCount
		def json = JsonOutput.toJson(list)
		//println(json)
		Map<String,Object> data = [totalTasks: totalTasks, pythonJobs: pythonTasks, ansibleJobs: ansibleTasks,
								   powerShellJobs: powerShellTasks, shellScriptJobs: shellScriptTasks, taskOrder: maporder, taskTable: outdemo, topFiveTasks: list, topFiveTasksJson: json]
		ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_HEADER, displayOrder: displayOrder++, dataMap: data)
        morpheus.report.appendResultRows(reportResult,[resultRowRecord]).blockingGet()
	}

	// https://developer.morpheusdata.com/api/com/morpheusdata/core/ReportProvider.html#method.summary
	// The description associated with the custom report
	 @Override
	 String getDescription() {
		 return "Automation overview"
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
	 List<OptionType> getOptionTypes() {}
 }