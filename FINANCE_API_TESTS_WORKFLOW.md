# GitHub Actions Workflow for Finance API Tests

This document contains the complete workflow file that you need to create in your repository.

## File Location
```
.github/workflows/finance-api-tests.yml
```

## Complete Workflow Content

Copy the entire content below and create a new file at `.github/workflows/finance-api-tests.yml` in your repository.

---

## How to Create This File

### Option 1: Using GitHub Web Interface
1. Go to your repository: https://github.com/vivekparmarbittu-ship-it/FInance_API_Automation
2. Click **Add file** → **Create new file**
3. In the "Name your file..." field, enter: `.github/workflows/finance-api-tests.yml`
4. Paste the content below into the editor
5. Click **Commit changes**

### Option 2: Using Git Command Line
```bash
# Create the directory structure
mkdir -p .github/workflows

# Create the file
touch .github/workflows/finance-api-tests.yml

# Add the content (copy and paste below)
# Then commit and push
git add .github/workflows/finance-api-tests.yml
git commit -m "Add GitHub Actions workflow for Finance API tests"
git push
```

---

## Workflow File Content

```yaml
name: Run Finance API Tests - Environment Based

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]
  workflow_dispatch:
    inputs:
      environment:
        description: 'Select environment to test'
        required: true
        default: 'SIT'
        type: choice
        options:
          - SIT
          - DEMO
          - QA

jobs:
  test-sit:
    runs-on: ubuntu-latest
    environment: SIT
    
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4
      
      - name: Set up Java
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: maven
      
      - name: Display Environment Info
        run: |
          echo "🔧 Environment: SIT"
          echo "🌐 Base URI: ${{ secrets.BASE_URI }}"
          echo "👤 Admin User: ${{ secrets.ADMIN_USERNAME }}"
      
      - name: Run API Tests - Admin Role
        env:
          BASE_URI: ${{ secrets.BASE_URI }}
          ADMIN_USERNAME: ${{ secrets.ADMIN_USERNAME }}
          ADMIN_PASSWORD: ${{ secrets.ADMIN_PASSWORD }}
        run: |
          echo "🧪 Running API tests as Admin..."
          # Replace with your actual test command
          # mvn test -Denv=SIT -Duser=$ADMIN_USERNAME -Dpassword=$ADMIN_PASSWORD
      
      - name: Run API Tests - Accountant Role
        env:
          BASE_URI: ${{ secrets.BASE_URI }}
          ACCOUNTANT_USERNAME: ${{ secrets.ACCOUNTANT_USERNAME }}
          ACCOUNTANT_PASSWORD: ${{ secrets.ACCOUNTANT_PASSWORD }}
        run: |
          echo "🧪 Running API tests as Accountant..."
          # Replace with your actual test command
          # mvn test -Denv=SIT -Duser=$ACCOUNTANT_USERNAME -Dpassword=$ACCOUNTANT_PASSWORD
      
      - name: Run API Tests - Senior Accountant Role
        env:
          BASE_URI: ${{ secrets.BASE_URI }}
          SENIOR_ACCOUNTANT_USERNAME: ${{ secrets.SENIOR_ACCOUNTANT_USERNAME }}
          SENIOR_ACCOUNTANT_PASSWORD: ${{ secrets.SENIOR_ACCOUNTANT_PASSWORD }}
        run: |
          echo "🧪 Running API tests as Senior Accountant..."
          # Replace with your actual test command
          # mvn test -Denv=SIT -Duser=$SENIOR_ACCOUNTANT_USERNAME -Dpassword=$SENIOR_ACCOUNTANT_PASSWORD
      
      - name: Run API Tests - Finance Controller Role
        env:
          BASE_URI: ${{ secrets.BASE_URI }}
          FINANCE_CONTROLLER_USERNAME: ${{ secrets.FINANCE_CONTROLLER_USERNAME }}
          FINANCE_CONTROLLER_PASSWORD: ${{ secrets.FINANCE_CONTROLLER_PASSWORD }}
        run: |
          echo "🧪 Running API tests as Finance Controller..."
          # Replace with your actual test command
          # mvn test -Denv=SIT -Duser=$FINANCE_CONTROLLER_USERNAME -Dpassword=$FINANCE_CONTROLLER_PASSWORD
      
      - name: Run API Tests - Senior Group Accounting Manager Role
        env:
          BASE_URI: ${{ secrets.BASE_URI }}
          SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME: ${{ secrets.SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME }}
          SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD: ${{ secrets.SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD }}
        run: |
          echo "🧪 Running API tests as Senior Group Accounting Manager..."
          # Replace with your actual test command
          # mvn test -Denv=SIT -Duser=$SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME -Dpassword=$SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD
      
      - name: Generate Test Report
        if: always()
        run: |
          echo "📊 Test execution completed for SIT environment"
      
      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-results-sit
          path: target/surefire-reports/
          retention-days: 30

  test-demo:
    runs-on: ubuntu-latest
    environment: DEMO
    if: github.event_name == 'workflow_dispatch' && github.event.inputs.environment == 'DEMO'
    
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4
      
      - name: Set up Java
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: maven
      
      - name: Display Environment Info
        run: |
          echo "🔧 Environment: DEMO"
          echo "🌐 Base URI: ${{ secrets.BASE_URI }}"
          echo "👤 Admin User: ${{ secrets.ADMIN_USERNAME }}"
      
      - name: Run API Tests - All Roles (DEMO)
        env:
          BASE_URI: ${{ secrets.BASE_URI }}
          ADMIN_USERNAME: ${{ secrets.ADMIN_USERNAME }}
          ADMIN_PASSWORD: ${{ secrets.ADMIN_PASSWORD }}
          ACCOUNTANT_USERNAME: ${{ secrets.ACCOUNTANT_USERNAME }}
          ACCOUNTANT_PASSWORD: ${{ secrets.ACCOUNTANT_PASSWORD }}
          SENIOR_ACCOUNTANT_USERNAME: ${{ secrets.SENIOR_ACCOUNTANT_USERNAME }}
          SENIOR_ACCOUNTANT_PASSWORD: ${{ secrets.SENIOR_ACCOUNTANT_PASSWORD }}
          FINANCE_CONTROLLER_USERNAME: ${{ secrets.FINANCE_CONTROLLER_USERNAME }}
          FINANCE_CONTROLLER_PASSWORD: ${{ secrets.FINANCE_CONTROLLER_PASSWORD }}
          SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME: ${{ secrets.SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME }}
          SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD: ${{ secrets.SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD }}
        run: |
          echo "🧪 Running all API tests on DEMO environment..."
          # mvn test -Denv=DEMO
      
      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-results-demo
          path: target/surefire-reports/
          retention-days: 30

  test-qa:
    runs-on: ubuntu-latest
    environment: QA
    if: github.event_name == 'workflow_dispatch' && github.event.inputs.environment == 'QA'
    
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4
      
      - name: Set up Java
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: maven
      
      - name: Display Environment Info
        run: |
          echo "🔧 Environment: QA"
          echo "🌐 Base URI: ${{ secrets.BASE_URI }}"
          echo "👤 Admin User: ${{ secrets.ADMIN_USERNAME }}"
      
      - name: Run API Tests - All Roles (QA)
        env:
          BASE_URI: ${{ secrets.BASE_URI }}
          ADMIN_USERNAME: ${{ secrets.ADMIN_USERNAME }}
          ADMIN_PASSWORD: ${{ secrets.ADMIN_PASSWORD }}
          ACCOUNTANT_USERNAME: ${{ secrets.ACCOUNTANT_USERNAME }}
          ACCOUNTANT_PASSWORD: ${{ secrets.ACCOUNTANT_PASSWORD }}
          SENIOR_ACCOUNTANT_USERNAME: ${{ secrets.SENIOR_ACCOUNTANT_USERNAME }}
          SENIOR_ACCOUNTANT_PASSWORD: ${{ secrets.SENIOR_ACCOUNTANT_PASSWORD }}
          FINANCE_CONTROLLER_USERNAME: ${{ secrets.FINANCE_CONTROLLER_USERNAME }}
          FINANCE_CONTROLLER_PASSWORD: ${{ secrets.FINANCE_CONTROLLER_PASSWORD }}
          SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME: ${{ secrets.SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME }}
          SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD: ${{ secrets.SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD }}
        run: |
          echo "🧪 Running all API tests on QA environment..."
          # mvn test -Denv=QA
      
      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-results-qa
          path: target/surefire-reports/
          retention-days: 30
```

---

## Workflow Explanation

### Triggers (on:)
- **push**: Runs on push to main or develop branches
- **pull_request**: Runs on PR creation/update
- **workflow_dispatch**: Allows manual trigger with environment selection

### Jobs

#### Job 1: test-sit
- Runs on **SIT environment** secrets
- Runs automatically on push/PR
- Tests all 5 roles sequentially
- Uploads test results as artifacts

#### Job 2: test-demo
- Runs on **DEMO environment** secrets
- Only runs on manual trigger when DEMO is selected
- Tests all roles together

#### Job 3: test-qa
- Runs on **QA environment** secrets
- Only runs on manual trigger when QA is selected
- Tests all roles together

---

## How to Use This Workflow

### Automatic Execution
The workflow automatically runs on:
1. Push to `main` or `develop` branches
2. Pull request to `main` or `develop` branches

### Manual Execution
To manually run tests on specific environments:

1. Go to your repository
2. Click **Actions** tab
3. Select **Run Finance API Tests - Environment Based** workflow
4. Click **Run workflow**
5. Select environment: **SIT**, **DEMO**, or **QA**
6. Click **Run workflow**

---

## Customization

### Update Test Commands
Replace the commented test commands with your actual Maven/Gradle commands:

```yaml
# Current (placeholder):
# mvn test -Denv=SIT -Duser=$ADMIN_USERNAME -Dpassword=$ADMIN_PASSWORD

# Example replacement:
mvn clean test -Denv=SIT -Dusername=$ADMIN_USERNAME -Dpassword=$ADMIN_PASSWORD -Dbase.url=$BASE_URI
```

### Adjust Java Version
If you need a different Java version, update this section:

```yaml
- name: Set up Java
  uses: actions/setup-java@v3
  with:
    java-version: '11'  # Change to 8, 11, 17, 21, etc.
    distribution: 'temurin'
    cache: maven
```

### Add More Steps
Add additional steps for reporting, notifications, or other actions as needed.

---

## Next Steps

1. ✅ Create and configure the 3 environments (SIT, DEMO, QA)
2. ✅ Add all secrets to each environment
3. ✅ Create the `.github/workflows/finance-api-tests.yml` file
4. 🔄 Update the test commands with your actual Maven/Gradle commands
5. 🔄 Push the workflow file to your repository
6. 🔄 Test the workflow by triggering it manually

---

## Support

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [GitHub Environments Documentation](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment)
- [GitHub Secrets Documentation](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions)
