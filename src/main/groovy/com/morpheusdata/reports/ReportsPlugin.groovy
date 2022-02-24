package com.morpheusdata.reports

import com.morpheusdata.core.Plugin
import com.morpheusdata.views.HandlebarsRenderer
import com.morpheusdata.views.ViewModel
import com.morpheusdata.web.Dispatcher


class ReportsPlugin extends Plugin {

	@Override
	String getCode() {
		return 'automation-report'
	}

	@Override
	void initialize() {
		def optionSourceProvider = new AutomationReportOptionSourceProvider(this, morpheus)
		AutomationAnalyticsReportProvider automationAnalyticsReportProvider = new AutomationAnalyticsReportProvider(this, morpheus)
		this.pluginProviders.put(automationAnalyticsReportProvider.code, automationAnalyticsReportProvider)
		AutomationOverviewReportProvider automationOverviewReportProvider = new AutomationOverviewReportProvider(this, morpheus)
		this.pluginProviders.put(automationOverviewReportProvider.code, automationOverviewReportProvider)
        this.pluginProviders.put(optionSourceProvider.code, optionSourceProvider)
		this.setName("Automation Insights")
		this.setDescription("Morpheus automation report plugin")
	}

	@Override
	void onDestroy() {
	}
}