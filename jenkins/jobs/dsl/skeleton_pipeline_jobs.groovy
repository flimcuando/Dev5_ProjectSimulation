// define project folder name
def projectFolderName = "DEV5_PROJECTSIMULATION_CARTRIDGE"

// define jobs
def buildMavenJob = freeStyleJob("${projectFolderName}/MAVEN_CARTRIDGE")
def buildSonarJob = freeStyleJob("${projectFolderName}/SONARQUBE_CARTRIDGE")
def buildNexusJob = freeStyleJob("${projectFolderName}/NEXUS_CARTRIDGE")
def buildAnsibleJob = freeStyleJob("${projectFolderName}/ANSIBLE_CARTRIDGE")
def buildSeleniumJob = freeStyleJob("${projectFolderName}/SELENIUM_CARTRIDGE")
def buildReleaseJob = freeStyleJob("${projectFolderName}/RELEASE_CARTRIDGE")

// view
def pipelineView = buildPipelineView("${projectFolderName}/DEV5_CARTRIDGE_CURRENCYCONVERTER_SIMULATION")

pipelineView.with{
	title('/DEV5_CARTRIDGE_CURRENCYCONVERTER_SIMULATION')
	displayedBuilds(3)
	selectedJob("${projectFolderName}/MAVEN_CARTRIDGE")
	showPipelineParameters()
	showPipelineDefinitionHeader()
	refreshFrequency(5)
	}

folder("${projectFolderName}") {
	displayName("${projectFolderName}")
	description("${projectFolderName}")
	}

buildMavenJob.with {
	// general
		properties {
			copyArtifactPermissionProperty {
			projectNames('NEXUS_CARTRIDGE')
			}
		}  

	// source code management  
		scm {
			git {
				remote {
					url('git@gitlab:Villegas/CurrencyConverterDTS.git')
					credentials('adop-jenkins-master')
				}
			}
		}

	// build triggers
		triggers {
			gitlabPush {
				buildOnMergeRequestEvents(true)
				buildOnPushEvents(true)
				enableCiSkip(false)
				setBuildDescription(false)
				rebuildOpenMergeRequest('never')
			}
		}
  
	// build environment
		wrappers {
			preBuildCleanup()
			}
  
	// build
		steps {
			maven{
				mavenInstallation('ADOP Maven')
				goals('package')
			}
		}

	// post build actions
		publishers {
			archiveArtifacts {
				pattern('**/*.war')
				onlyIfSuccessful()
			}
			downstream('SONARQUBE_CARTRIDGE', 'SUCCESS')
		}
	}


buildSonarJob.with {
	// source code management  
		scm {
			git {
				remote {
					url('git@gitlab:Villegas/CurrencyConverterDTS.git')
					credentials('adop-jenkins-master')
				}
			}
		}

	// build
		steps {
			configure { project ->
				project / 'builders' / 'hudson.plugins.sonar.SonarRunnerBuilder' {
					properties('''sonar.projectKey=SonarActivityTest
					sonar.projectName=simulationActivity
					sonar.projectVersion=1
					sonar.sources=.''')
					javaOpts()
					jdk('(Inherit From Job)')
					task()
					}
				}
		}

	// post build actions
		publishers {
			downstream('NEXUS_CARTRIDGE', 'SUCCESS')
		}
	}


buildNexusJob.with {
	// build
		steps {
			copyArtifacts('MAVEN_CARTRIDGE') {
				includePatterns('target/*.war', '*.properties')
				buildSelector {
					latestSuccessful(true)
				}
			}
		}
  
		steps {
			nexusArtifactUploader {
			nexusVersion('NEXUS2')
			protocol('HTTP')
			nexusUrl('nexus:8081/nexus')
			groupId('DTSActivity')
			version('1')
			repository('snapshots')
			credentialsId('5c0a05a0-9d55-4163-bb73-72b30d0b90a1')
			artifact {
				artifactId('CurrencyConverter')
				type('war')
				file('/var/jenkins_home/jobs/DEV5_PROJECTSIMULATION_CARTRIDGE/jobs/NEXUS_CARTRIDGE/workspace/target/CurrencyConverter.war')
				}
			}
		}

	// post build actions
		publishers {
			archiveArtifacts {
				pattern('**/*.war')
				onlyIfSuccessful()
			}
			downstream('ANSIBLE_CARTRIDGE', 'SUCCESS')
		}
	}


buildAnsibleJob.with {
	// general
		label('ansible')
  
	// scm
		scm {
			git {
				remote {
					url('http://gitlab/gitlab/Villegas/Ansible-Activity.git')
					credentials('5c0a05a0-9d55-4163-bb73-72b30d0b90a1')
				}
			}
		}
  
	// build environment
		steps{
		wrappers {
			colorizeOutput('xterm')
			sshAgent('adop-jenkins-master')
			credentialsBinding {
				usernamePassword('username', 'password', '5c0a05a0-9d55-4163-bb73-72b30d0b90a1')
				}
			shell('ansible-playbook -i hosts master.yml -u ec2-user -e "image_version=${BUILD_NUMBER} username=$username password=$password"')
			}
		}
  
	// post build
		publishers {
			downstream('SELENIUM_CARTRIDGE', 'SUCCESS')
			}
	}


buildSeleniumJob.with {
	// scm
		scm {
			git {
				remote {
					url('http://gitlab/gitlab/Villegas/SeleniumDTS.git')
					credentials('5c0a05a0-9d55-4163-bb73-72b30d0b90a1')
				}
			}
		}

	// build
		steps {
			maven{
				mavenInstallation('ADOP Maven')
				goals('test')
			}
		}

	// post build actions
		publishers {
			downstream('RELEASE_CARTRIDGE', 'SUCCESS')
		}
	}


buildReleaseJob.with {
	// general
		properties {
			copyArtifactPermissionProperty {
				projectNames('NEXUS_CARTRIDGE')
				}
			}  
  
	// build
		steps {
			copyArtifacts('NEXUS_CARTRIDGE') {
				includePatterns('**/*.war')
				buildSelector {
					latestSuccessful(true)
				}
			}
		}
  
		steps {
			nexusArtifactUploader {
				nexusVersion('NEXUS2')
				protocol('HTTP')
				nexusUrl('nexus:8081/nexus')
				groupId('DTSActivity')
				version('1')
				repository('releases')
				credentialsId('5c0a05a0-9d55-4163-bb73-72b30d0b90a1')
				artifact {
					artifactId('CurrencyConverter')
					type('war')
					file('target/CurrencyConverter.war')
				}
			}
		}
	}