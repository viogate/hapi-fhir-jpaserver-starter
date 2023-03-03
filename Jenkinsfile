import groovy.json.JsonSlurper

@Library('viollier') _
import be.cegeka.jenkins.*

def version = ""
def SSH_PRIVATE_KEY = 'ssh-github'
def success = true

pipeline {
    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    agent {
        // the node is selected by label, and only has 1 JDK installed
        // that matches the label
        node { label 'java17' }
    }
    triggers {
        pollSCM('H/5 * * * *')
    }
    stages {
        stage('dakke') {
            steps {
                echo "hello"
            }
        }
//        stage('Determine version') {
//            steps {
//                sshagent(credentials: [SSH_PRIVATE_KEY]) {
//                    sh "git fetch --all --tags"
//                    sh "git checkout -f -B ${env.BRANCH_NAME} HEAD"
//                }
//                script {
//                    def prefix = env.BRANCH_NAME.replaceAll('_', '.').replaceAll('viollier.', '') + "-vio-"
//                    def lastBranchTag = sh(script: "git tag -l '${prefix}[0-9]*' | sort -rn -t . -k 3 | head -1", returnStdout: true).trim()
//                    if (!lastBranchTag.isEmpty()) {
//                        println "Found previous branch build with tag: '${lastBranchTag}'"
//                        def suffix = Integer.parseInt(lastBranchTag.split("-").last()) + 1
//                        version = "${prefix}${suffix}"
//                    } else {
//                        println "New branch. Branch builds will be prefixed with: '${prefix}'"
//                        version = "${prefix}1"
//                    }
//                    echo "Feature branch, next version is: ${version}"
//
//                    currentBuild.description = "Artifact version $version"
//                }
//            }
//        }
//        stage('Test') {
//            steps {
//                sh "mvn clean test"
//            }
//        }
//        stage('Build') {
//            steps {
//                sh "./build-docker-image.sh ${version}"
//            }
//        }
//        stage('Tag') {
//            // should we tag in post { success { ... } } ?
//            // for sure: use Git SCM in Multibranch pipeline to benefit SSH key based auth
//            //           GitHub SCM only not supports username/password based authentication
//            when {
//                expression { success }
//            }
//            steps {
//                sshagent(credentials: [SSH_PRIVATE_KEY]) {
//                    sh "git tag ${version}"
//                    sh "git config credential.username ${env.GIT_USER}"
//                    sh "git config credential.helper '!echo password=\${GIT_PWD}; echo'"
//                    sh "GIT_ASKPASS=true git push origin --tags"
//                    // clean up git credentials
//                    sh "git config --unset credential.helper"
//                }
//
//            }
//        }
//        stage('Publish') {
//            when {
//                expression { success }
//            }
//            steps {
//                sh "docker push docker-dev.artifactory.viollier.ch/hapi-fhir-jpaserver-starter:${version}"
//            }
//        }
    }
//    post {
//        //always {
//        //    junit '**/build/test-results/**/*.xml'
//        //}
//        cleanup {
//            echo "Clearing docker containers from version ${version}"
//            sh "set +e"
//            sh "docker rmi docker-dev.artifactory.viollier.ch/hapi-fhir-jpaserver-starter:${version}"
//
//            sh "docker image prune -f"
//
//            sh "set -e"
//        }
//    }

}
