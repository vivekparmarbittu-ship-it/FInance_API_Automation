# Step-by-Step Visual Guide: Adding GitHub Secrets & Environments

This guide will walk you through adding secrets to your GitHub repository using the web interface.

---

## **SECTION 1: Create GitHub Environments**

### Step 1.1: Navigate to Settings
1. Go to your repository: https://github.com/vivekparmarbittu-ship-it/FInance_API_Automation
2. Click the **Settings** tab at the top

```
Repository URL: vivekparmarbittu-ship-it/FInance_API_Automation
                ↓
        [Code] [Issues] [Pull requests] [Settings] ← CLICK HERE
```

---

### Step 1.2: Access Environments Section
1. In the left sidebar, scroll down to find **Environments**
2. Click on **Environments**

```
Left Sidebar Menu:
├── General
├── Access
├── Code and automation
│   ├── Actions
│   ├── Secrets and variables
│   ├── Dependabot
│   └── Code security and analysis
├── Pull requests
└── Environments ← CLICK HERE
```

---

### Step 1.3: Create SIT Environment
1. Click the green **New environment** button
2. In the "Environment name" field, type: **SIT**
3. Click **Configure environment**

```
┌─────────────────────────────────┐
│  New environment                │
├─────────────────────────────────┤
│ Environment name:               │
│ ┌─────────────────────────────┐ │
│ │ SIT                         │ │
│ └─────────────────────────────┘ │
│                                 │
│        [Configure environment]  │
└─────────────────────────────────┘
```

---

### Step 1.4: Configure SIT Environment
1. You're now in the SIT environment configuration page
2. You can leave default settings as-is for now
3. Scroll down to **Secrets** section (we'll add secrets next)

---

### Step 1.5: Create DEMO Environment
1. Click **Environments** in the left sidebar again
2. Click **New environment**
3. Enter name: **DEMO**
4. Click **Configure environment**

---

### Step 1.6: Create QA Environment
1. Click **Environments** in the left sidebar again
2. Click **New environment**
3. Enter name: **QA**
4. Click **Configure environment**

---

## **SECTION 2: Add Secrets to SIT Environment**

### Step 2.1: Go to SIT Environment
1. Click **Environments** in the left sidebar
2. Click on **SIT** environment

```
Environments List:
├── SIT ← CLICK HERE
├── DEMO
└── QA
```

---

### Step 2.2: Start Adding Secrets
1. Scroll down to **Secrets** section
2. Click **Add secret** button

```
┌──────────────────────────────────┐
│ Secrets                          │
├──────────────────────────────────┤
│ [Add secret] ← CLICK HERE        │
└──────────────────────────────────┘
```

---

### Step 2.3: Add BASE_URI Secret
1. Secret name: **BASE_URI**
2. Secret value: **https://one-sit.humain.ai**
3. Click **Add secret**

```
┌──────────────────────────────────┐
│ Add a new secret                 │
├──────────────────────────────────┤
│ Name:                            │
│ ┌──────────────────────────────┐ │
│ │ BASE_URI                     │ │
│ └──────────────────────────────┘ │
│                                  │
│ Value:                           │
│ ┌──────────────────────────────┐ │
│ │ https://one-sit.humain.ai    │ │
│ └──────────────────────────────┘ │
│                                  │
│ [Add secret] ← CLICK            │
└──────────────────────────────────┘
```

---

### Step 2.4: Add ADMIN_USERNAME Secret
1. Click **Add secret** again
2. Secret name: **ADMIN_USERNAME**
3. Secret value: **lokesh.patidar@visionwaves.com**
4. Click **Add secret**

---

### Step 2.5: Add ADMIN_PASSWORD Secret
1. Click **Add secret**
2. Secret name: **ADMIN_PASSWORD**
3. Secret value: **Vision@123**
4. Click **Add secret**

---

### Step 2.6: Add ACCOUNTANT_USERNAME Secret
1. Click **Add secret**
2. Secret name: **ACCOUNTANT_USERNAME**
3. Secret value: **Vivek.parmar@visionwaves.com**
4. Click **Add secret**

---

### Step 2.7: Add ACCOUNTANT_PASSWORD Secret
1. Click **Add secret**
2. Secret name: **ACCOUNTANT_PASSWORD**
3. Secret value: **Vision@123**
4. Click **Add secret**

---

### Step 2.8: Add SENIOR_ACCOUNTANT_USERNAME Secret
1. Click **Add secret**
2. Secret name: **SENIOR_ACCOUNTANT_USERNAME**
3. Secret value: **Khushbu.patel@visionwaves.com**
4. Click **Add secret**

---

### Step 2.9: Add SENIOR_ACCOUNTANT_PASSWORD Secret
1. Click **Add secret**
2. Secret name: **SENIOR_ACCOUNTANT_PASSWORD**
3. Secret value: **Vision@123**
4. Click **Add secret**

---

### Step 2.10: Add FINANCE_CONTROLLER_USERNAME Secret
1. Click **Add secret**
2. Secret name: **FINANCE_CONTROLLER_USERNAME**
3. Secret value: **abinaya.saravanan@visionwaves.com**
4. Click **Add secret**

---

### Step 2.11: Add FINANCE_CONTROLLER_PASSWORD Secret
1. Click **Add secret**
2. Secret name: **FINANCE_CONTROLLER_PASSWORD**
3. Secret value: **Vision@123**
4. Click **Add secret**

---

### Step 2.12: Add SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME Secret
1. Click **Add secret**
2. Secret name: **SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME**
3. Secret value: **Suyash.tiwari@visionwaves.com**
4. Click **Add secret**

---

### Step 2.13: Add SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD Secret
1. Click **Add secret**
2. Secret name: **SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD**
3. Secret value: **Vision@123**
4. Click **Add secret**

---

### ✅ SIT Environment Complete!

You should now see all 11 secrets listed in the SIT environment (masked with dots):

```
SIT Environment Secrets:
✓ BASE_URI
✓ ADMIN_USERNAME
✓ ADMIN_PASSWORD
✓ ACCOUNTANT_USERNAME
✓ ACCOUNTANT_PASSWORD
✓ SENIOR_ACCOUNTANT_USERNAME
✓ SENIOR_ACCOUNTANT_PASSWORD
✓ FINANCE_CONTROLLER_USERNAME
✓ FINANCE_CONTROLLER_PASSWORD
✓ SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME
✓ SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD
```

---

## **SECTION 3: Add Secrets to DEMO Environment**

### Step 3.1: Navigate to DEMO Environment
1. Click **Environments** in the left sidebar
2. Click on **DEMO** environment

---

### Step 3.2: Add All Secrets to DEMO
Follow the exact same steps as SIT (Steps 2.2 - 2.13), but use these values:

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

**Note:** Only the **BASE_URI** is different for DEMO. All usernames and passwords remain the same.

---

## **SECTION 4: Add Secrets to QA Environment**

### Step 4.1: Navigate to QA Environment
1. Click **Environments** in the left sidebar
2. Click on **QA** environment

---

### Step 4.2: Add All Secrets to QA
Follow the exact same steps as SIT, but use these values:

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

**Note:** Only the **BASE_URI** is different for QA. All usernames and passwords remain the same.

---

## **SECTION 5: Verify All Secrets Are Added**

### Step 5.1: Check Each Environment
1. Click **Environments** in the left sidebar
2. Click on **SIT** and verify you see all 11 secrets (masked)
3. Click on **DEMO** and verify you see all 11 secrets (masked)
4. Click on **QA** and verify you see all 11 secrets (masked)

```
Each environment should show:
✓ BASE_URI
✓ ADMIN_USERNAME
✓ ADMIN_PASSWORD
✓ ACCOUNTANT_USERNAME
✓ ACCOUNTANT_PASSWORD
✓ SENIOR_ACCOUNTANT_USERNAME
✓ SENIOR_ACCOUNTANT_PASSWORD
✓ FINANCE_CONTROLLER_USERNAME
✓ FINANCE_CONTROLLER_PASSWORD
✓ SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME
✓ SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD
```

---

## **SECTION 6: Quick Reference - Copy & Paste Tables**

### SIT Environment Secrets

| # | Secret Name | Value |
|---|---|---|
| 1 | BASE_URI | https://one-sit.humain.ai |
| 2 | ADMIN_USERNAME | lokesh.patidar@visionwaves.com |
| 3 | ADMIN_PASSWORD | Vision@123 |
| 4 | ACCOUNTANT_USERNAME | Vivek.parmar@visionwaves.com |
| 5 | ACCOUNTANT_PASSWORD | Vision@123 |
| 6 | SENIOR_ACCOUNTANT_USERNAME | Khushbu.patel@visionwaves.com |
| 7 | SENIOR_ACCOUNTANT_PASSWORD | Vision@123 |
| 8 | FINANCE_CONTROLLER_USERNAME | abinaya.saravanan@visionwaves.com |
| 9 | FINANCE_CONTROLLER_PASSWORD | Vision@123 |
| 10 | SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME | Suyash.tiwari@visionwaves.com |
| 11 | SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD | Vision@123 |

### DEMO Environment Secrets

| # | Secret Name | Value |
|---|---|---|
| 1 | BASE_URI | https://one-demo.humain.ai |
| 2 | ADMIN_USERNAME | lokesh.patidar@visionwaves.com |
| 3 | ADMIN_PASSWORD | Vision@123 |
| 4 | ACCOUNTANT_USERNAME | Vivek.parmar@visionwaves.com |
| 5 | ACCOUNTANT_PASSWORD | Vision@123 |
| 6 | SENIOR_ACCOUNTANT_USERNAME | Khushbu.patel@visionwaves.com |
| 7 | SENIOR_ACCOUNTANT_PASSWORD | Vision@123 |
| 8 | FINANCE_CONTROLLER_USERNAME | abinaya.saravanan@visionwaves.com |
| 9 | FINANCE_CONTROLLER_PASSWORD | Vision@123 |
| 10 | SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME | Suyash.tiwari@visionwaves.com |
| 11 | SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD | Vision@123 |

### QA Environment Secrets

| # | Secret Name | Value |
|---|---|---|
| 1 | BASE_URI | https://qa.visionwaves.com |
| 2 | ADMIN_USERNAME | lokesh.patidar@visionwaves.com |
| 3 | ADMIN_PASSWORD | Vision@123 |
| 4 | ACCOUNTANT_USERNAME | Vivek.parmar@visionwaves.com |
| 5 | ACCOUNTANT_PASSWORD | Vision@123 |
| 6 | SENIOR_ACCOUNTANT_USERNAME | Khushbu.patel@visionwaves.com |
| 7 | SENIOR_ACCOUNTANT_PASSWORD | Vision@123 |
| 8 | FINANCE_CONTROLLER_USERNAME | abinaya.saravanan@visionwaves.com |
| 9 | FINANCE_CONTROLLER_PASSWORD | Vision@123 |
| 10 | SENIOR_GROUP_ACCOUNTING_MANAGER_USERNAME | Suyash.tiwari@visionwaves.com |
| 11 | SENIOR_GROUP_ACCOUNTING_MANAGER_PASSWORD | Vision@123 |

---

## **SECTION 7: Troubleshooting**

### Q: I don't see the Environments option in the left sidebar
**A:** Make sure you're in the **Settings** tab of your repository, not elsewhere. Environments are only visible in Settings.

### Q: Can I see the secret values after I add them?
**A:** No, for security reasons, GitHub masks all secret values. You can only see that a secret exists, not its value. If you need to change a secret, you must delete and re-add it.

### Q: How do I edit a secret?
**A:** 
1. Click the secret name
2. Click **Update secret**
3. Enter the new value
4. Click **Update secret**

### Q: Can I delete a secret?
**A:** Yes, click the secret, scroll down, and click **Delete secret**.

### Q: What if I make a typo in a secret value?
**A:** 
1. Click on the secret
2. Click **Update secret**
3. Enter the correct value
4. Click **Update secret**

---

## **SECTION 8: Next Steps - Add GitHub Actions Workflow**

Once you've added all the secrets, create the workflow file:

1. Go to your repository main page
2. Click **Add file** → **Create new file**
3. File path: `.github/workflows/finance-api-tests.yml`
4. Copy the workflow content from `FINANCE_API_TESTS_WORKFLOW.md`
5. Click **Commit changes**

The workflow will automatically use secrets from the specified environment when it runs.

---

## **SECTION 9: Summary Checklist**

- [ ] Created SIT environment
- [ ] Created DEMO environment
- [ ] Created QA environment
- [ ] Added all 11 secrets to SIT
- [ ] Added all 11 secrets to DEMO
- [ ] Added all 11 secrets to QA
- [ ] Verified all secrets are visible (masked) in each environment
- [ ] Ready to create GitHub Actions workflow

---

## **Support & Documentation**

- [GitHub Environments Documentation](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment)
- [GitHub Secrets Documentation](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

---

**You're all set!** All environments and secrets are now configured. Your GitHub Actions workflows can now access these secrets using `${{ secrets.SECRET_NAME }}` syntax.
