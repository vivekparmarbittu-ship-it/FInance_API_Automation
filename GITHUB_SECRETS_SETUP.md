# GitHub Secrets & Environments Setup Guide

This document provides step-by-step instructions to set up GitHub Environments and Secrets for the Finance API Automation project.

## Overview

We will create 3 environments (SIT, DEMO, QA) with role-based credentials. This allows different test runs to use different environment configurations and user credentials.

---

## Step 1: Create GitHub Environments

### 1.1 Navigate to Repository Settings
1. Go to your repository: `vivekparmarbittu-ship-it/FInance_API_Automation`
2. Click **Settings** (top menu)
3. In the left sidebar, click **Environments**

### 1.2 Create First Environment: SIT
1. Click **New environment**
2. Enter environment name: `SIT`
3. Click **Configure environment**

### 1.3 Create Second Environment: DEMO
1. Click **New environment**
2. Enter environment name: `DEMO`
3. Click **Configure environment**

### 1.4 Create Third Environment: QA
1. Click **New environment**
2. Enter environment name: `QA`
3. Click **Configure environment**

---

## Step 2: Add Secrets to SIT Environment

Once you're in the SIT environment configuration:

1. Scroll down to **Secrets** section
2. Click **Add secret** for each of the following:

### SIT Environment Secrets

| Secret Name | Value |
|---|---|
| BASE_URI | `https://one-sit.humain.ai` |
| ADMIN_USERNAME | `lokesh.patidar@visionwaves.com` |
| ADMIN_PASSWORD | `Vision@123` |
| ACCOUNTANT_USERNAME | `Vivek.parmar@visionwaves.com` |
| ACCOUNTANT_PASSWORD | `Vision@123` |
| SENIOR_ACCOUNTANT_USERNAME | `Khushbu.patel@visionwaves.com` |
| SENIOR_ACCOUNTANT_PASSWORD | `Vision@123` |
| FINANCE_CONTROLLER_USERNAME | `abinaya.saravanan@visionwaves.com` |
| FINANCE_CONTROLLER_PASSWORD | `Vision@123` |
| SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME | `Suyash.tiwari@visionwaves.com` |
| SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD | `Vision@123` |

**How to add each secret:**
1. Click **Add secret**
2. Enter the secret name (from table above)
3. Enter the value (from table above)
4. Click **Add secret**

---

## Step 3: Add Secrets to DEMO Environment

Navigate to DEMO environment and add the same secrets with DEMO base URI:

### DEMO Environment Secrets

| Secret Name | Value |
|---|---|
| BASE_URI | `https://one-demo.humain.ai` |
| ADMIN_USERNAME | `lokesh.patidar@visionwaves.com` |
| ADMIN_PASSWORD | `Vision@123` |
| ACCOUNTANT_USERNAME | `Vivek.parmar@visionwaves.com` |
| ACCOUNTANT_PASSWORD | `Vision@123` |
| SENIOR_ACCOUNTANT_USERNAME | `Khushbu.patel@visionwaves.com` |
| SENIOR_ACCOUNTANT_PASSWORD | `Vision@123` |
| FINANCE_CONTROLLER_USERNAME | `abinaya.saravanan@visionwaves.com` |
| FINANCE_CONTROLLER_PASSWORD | `Vision@123` |
| SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME | `Suyash.tiwari@visionwaves.com` |
| SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD | `Vision@123` |

---

## Step 4: Add Secrets to QA Environment

Navigate to QA environment and add the same secrets (adjust BASE_URI if needed):

### QA Environment Secrets

| Secret Name | Value |
|---|---|
| BASE_URI | `https://qa.visionwaves.com` |
| ADMIN_USERNAME | `lokesh.patidar@visionwaves.com` |
| ADMIN_PASSWORD | `Vision@123` |
| ACCOUNTANT_USERNAME | `Vivek.parmar@visionwaves.com` |
| ACCOUNTANT_PASSWORD | `Vision@123` |
| SENIOR_ACCOUNTANT_USERNAME | `Khushbu.patel@visionwaves.com` |
| SENIOR_ACCOUNTANT_PASSWORD | `Vision@123` |
| FINANCE_CONTROLLER_USERNAME | `abinaya.saravanan@visionwaves.com` |
| FINANCE_CONTROLLER_PASSWORD | `Vision@123` |
| SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME | `Suyash.tiwari@visionwaves.com` |
| SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD | `Vision@123` |

---

## Step 5: Repository-Level Secrets (Optional)

If you need secrets that are shared across all environments, go to:
- **Settings → Secrets and variables → Actions**

Add any global secrets here (e.g., CLIENT_ID, API_KEYS, etc.)

---

## Step 6: Using Secrets in Workflows

Once environments and secrets are set up, use them in your GitHub Actions workflows:

### Example: Using SIT Environment Secrets

```yaml
name: Run Tests - SIT Environment

on: [push, pull_request]

jobs:
  test-sit:
    runs-on: ubuntu-latest
    environment: SIT  # This tells GitHub to use SIT environment secrets
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Run API Tests
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
          # Your test commands here
          echo "Running tests for SIT environment: $BASE_URI"
```

---

## Step 7: Verify Setup

1. Navigate to **Settings → Environments**
2. You should see: `SIT`, `DEMO`, `QA`
3. Click each environment to verify all secrets are present
4. Secrets will show as masked (●●●●●) for security

---

## Important Notes

- **Secrets are encrypted** and cannot be viewed once saved
- Each environment can have **different values** for the same secret name
- Workflows specify which environment to use with `environment: ENV_NAME`
- Secrets are only available to workflows when targeting that environment
- For security, never commit secrets to your repository

---

## Support

For issues or questions about GitHub Secrets, refer to:
- [GitHub Docs: Using secrets in GitHub Actions](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions)
- [GitHub Docs: Using environments for deployment](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment)
