package com.morpheusdata.reports

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.*
import groovy.util.logging.Slf4j
import com.morpheusdata.core.OptionSourceProvider

@Slf4j
class AutomationReportOptionSourceProvider implements OptionSourceProvider {

	Plugin plugin
	MorpheusContext morpheusContext

	AutomationReportOptionSourceProvider(Plugin plugin, MorpheusContext context) {
		this.plugin = plugin
		this.morpheusContext = context
	}

	@Override
	MorpheusContext getMorpheus() {
		return this.morpheusContext
	}

	@Override
	Plugin getPlugin() {
		return this.plugin
	}

	@Override
	String getCode() {
		return 'automationReport'
	}

	@Override
	String getName() {
		return 'Automation Report Duration'
	}

	@Override
	List<String> getMethodNames() {
		return new ArrayList<String>(['durationOptions'])
	}

	List durationOptions(args) {
		[
				[name: '7 Days', value: '7'],
				[name: '30 Days', value: '30'],
				[name: '60 Days', value: '60'],
				[name: '90 Days', value: '90']
		]
	}
}