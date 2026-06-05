pipeline {
    agent any

    parameters {
        choice(name: 'ENV', choices: ['Demo', 'DEV', 'SIT', 'HuaminDev', 'QA'], description: 'Select the target Environment')
        choice(name: 'MODULE', choices: ['financecrudsanity', 'finance', 'financesanity', 'financeunauthorized', 'creditmemo', 'debitmemo', 'invoice'], description: 'Select the Test Module / Suite to run')
        string(name: 'GROUPS', defaultValue: '', description: 'Optional: Include TestNG groups (comma-separated, e.g. sanity,regression)')
        string(name: 'EXCLUDE_GROUPS', defaultValue: '', description: 'Optional: Exclude TestNG groups (comma-separated)')
        string(name: 'SINGLE_CLASS', defaultValue: '', description: 'Optional: Fully qualified test class name to run a single test class (overrides Module selection)')
    }

    stages {
        stage('Checkout') {
            steps {
                // Check out the code from SCM repository
                checkout scm
            }
        }

        stage('Build executable JAR') {
            steps {
                echo 'Building fat executable JAR with tests included...'
                // Cleans and packages the shaded JAR containing all dependencies and test classes
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Execute Tests') {
            steps {
                script {
                    def cmd = "java -Denv=${params.ENV}"
                    
                    // Single class execution takes priority if specified
                    if (params.SINGLE_CLASS && params.SINGLE_CLASS.trim() != '') {
                        echo "Running single class: ${params.SINGLE_CLASS.trim()}"
                        cmd += " -Dclass=${params.SINGLE_CLASS.trim()}"
                    } else {
                        echo "Running module: ${params.MODULE}"
                        cmd += " -Dmodule=${params.MODULE}"
                    }

                    // Apply Groups filter if specified
                    if (params.GROUPS && params.GROUPS.trim() != '') {
                        echo "Applying group filter: ${params.GROUPS.trim()}"
                        cmd += " -Dgroups=${params.GROUPS.trim()}"
                    }

                    // Apply Exclude Groups filter if specified
                    if (params.EXCLUDE_GROUPS && params.EXCLUDE_GROUPS.trim() != '') {
                        echo "Applying exclude group filter: ${params.EXCLUDE_GROUPS.trim()}"
                        cmd += " -DexcludeGroups=${params.EXCLUDE_GROUPS.trim()}"
                    }

                    // Append the JAR target
                    cmd += " -jar target/FinanceAccounting.jar"

                    echo "Executing command: ${cmd}"
                    sh cmd
                }
            }
        }
    }

    post {
        always {
            echo 'Archiving test reports...'
            
            // Archive TestNG default reports
            archiveArtifacts artifacts: 'target/surefire-reports/**/*', allowEmptyArchive: true

            // Archive Allure raw results
            archiveArtifacts artifacts: 'allure-results/**/*', allowEmptyArchive: true
            
            // Generate and publish the Allure Report if the Allure Jenkins Plugin is installed
            allure includeProperties: false, jdk: '', results: [[path: 'allure-results']]
        }
    }
}
