#!groovy

pipeline {
  agent any
  options {
    disableConcurrentBuilds()
    durabilityHint('PERFORMANCE_OPTIMIZED')
    buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '5'))
    timeout(time: 120, unit: 'MINUTES')
  }
  stages {
    stage( "Parallel Stage" ) {
      parallel {
        stage( "Build / Test - JDK8" ) {
          agent { node { label 'linux' } }
          options { timeout( time: 120, unit: 'MINUTES' ) }
          steps {
            mavenBuild( "jdk8", "clean install javadoc:jar" )
            // Collect up the jacoco execution results
            jacoco inclusionPattern: '**/org/eclipse/jetty/**/*.class',
                   exclusionPattern: '',
                   execPattern: '**/target/jacoco.exec',
                   classPattern: '**/target/classes',
                   sourcePattern: '**/src/main/java'
            warnings consoleParsers: [[parserName: 'Maven'], [parserName: 'Java']]
            junit testResults: '**/target/surefire-reports/*.xml,**/target/invoker-reports/TEST*.xml'
            script {
              if (env.BRANCH_NAME == 'master') {
                mavenBuild( "jdk8", "deploy" )
              }
            }
          }
        }
        stage( "Build / Test - JDK11" ) {
          agent { node { label 'linux' } }
          options { timeout( time: 120, unit: 'MINUTES' ) }
          steps {
            mavenBuild( "jdk11", "clean install javadoc:jar" )
            junit testResults: '**/target/surefire-reports/*.xml,**/target/invoker-reports/TEST*.xml'
          }
        }
        stage( "Build / Test - JDK14" ) {
          agent { node { label 'linux' } }
          options { timeout( time: 120, unit: 'MINUTES' ) }
          steps {
            mavenBuild( "jdk14", "clean install javadoc:jar" )
            junit testResults: '**/target/surefire-reports/*.xml,**/target/invoker-reports/TEST*.xml'
          }
        }
      }
    }
  }
}

/**
 * To other developers, if you are using this method above, please use the following syntax.
 *
 * mavenBuild("<jdk>", "<profiles> <goals> <plugins> <properties>"
 *
 * @param jdk the jdk tool name (in jenkins) to use for this build
 * @param cmdline the command line in "<profiles> <goals> <properties>"`format.
 * @return the Jenkinsfile step representing a maven build
 */
def mavenBuild(jdk, cmdline) {
  def mvnName = 'maven3.5'
  def localRepo = "${env.JENKINS_HOME}/${env.EXECUTOR_NUMBER}" // ".repository" //
  def settingsName = 'oss-settings.xml'
  def mavenOpts = '-Xms2g -Xmx2g -Djava.awt.headless=true'

  withMaven(
          maven: mvnName,
          jdk: "$jdk",
          publisherStrategy: 'EXPLICIT',
          globalMavenSettingsConfig: settingsName,
          options: [junitPublisher(disabled: true)],
          mavenOpts: mavenOpts,
          mavenLocalRepo: localRepo) {
    // Some common Maven command line + provided command line
    sh "mvn -V -B -DfailIfNoTests=false -Dmaven.test.failure.ignore=true -e $cmdline"
  }
}

// vim: et:ts=2:sw=2:ft=groovy
