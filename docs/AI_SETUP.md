# 🤖 AI Setup Guide

This guide shows you how to set up AI providers (OpenAI, Anthropic, Google Gemini, AWS Bedrock) for smarter documentation generation.

## 🎯 Why Use AI?

The app works **without AI** (using Mock mode). But with AI, you get:

- ✅ Better business logic understanding
- ✅ Smarter feature detection
- ✅ User-facing descriptions
- ✅ Business rules inference

## 📋 Supported Providers

| Provider | Best For | Cost | Speed |
|----------|----------|------|-------|
| **Google Gemini** | Beginners, Free tier | 💰 Free tier available | ⚡⚡⚡ Fast |
| **OpenAI GPT-4** | Best quality | 💰💰 ~$0.01/1K tokens | ⚡⚡ Medium |
| **Anthropic Claude** | Long context | 💰💰 ~$0.015/1K tokens | ⚡⚡ Medium |
| **AWS Bedrock** | Enterprise, AWS users | 💰💰💰 Pay per use | ⚡⚡ Medium |
| **Mock** | Offline, No cost | 💰 Free | ⚡⚡⚡⚡ Instant |

---

## 1️⃣ Google Gemini (Recommended for Beginners)

### Why Gemini?
- ✅ **Free tier**: 60 requests/minute
- ✅ **Easy setup**: Just one API key
- ✅ **Good quality**: Gemini Pro is powerful
- ✅ **Fast**: Response in ~2 seconds

### Step-by-Step Setup

**1. Get Your API Key**

Go to: https://makersuite.google.com/app/apikey

- Click "Get API key"
- Click "Create API key in new project"
- Copy your key (starts with `AIza...`)

**2. Configure the App**

```bash
# Set environment variable
export LLM_API_KEY="AIzaSy..."  # Your Gemini API key
export LLM_PROVIDER="gemini"

# Or add to docker-compose.yml
services:
  api:
    environment:
      - LLM_API_KEY=AIzaSy...
      - LLM_PROVIDER=gemini
```

**3. Restart**

```bash
docker-compose restart api
```

**4. Test It**

```bash
# Check logs
docker-compose logs api | grep -i gemini

# Should see: "Using Google Gemini client with model: gemini-pro"
```

### Available Models

```yaml
# In application.yml
docgen:
  llm:
    provider: gemini
    model: gemini-pro  # Default, good for most cases
    # Or: gemini-pro-vision  # For image analysis (future)
```

---

## 2️⃣ OpenAI (GPT-4)

### Why OpenAI?
- ✅ **Best quality**: GPT-4 is very accurate
- ✅ **Structured output**: Great for JSON responses
- ✅ **Reliable**: Stable API
- ⚠️ **Paid only**: No free tier

### Step-by-Step Setup

**1. Get Your API Key**

Go to: https://platform.openai.com/api-keys

- Sign up / Log in
- Click "Create new secret key"
- Copy your key (starts with `sk-proj-...`)
- **Add payment method** (required)

**2. Configure the App**

```bash
export LLM_API_KEY="sk-proj-..."
export LLM_PROVIDER="openai"
export LLM_MODEL="gpt-4-turbo-preview"  # Optional
```

Or in `application.yml`:

```yaml
docgen:
  llm:
    enabled: true
    provider: openai
    api-key: sk-proj-...
    model: gpt-4-turbo-preview
```

**3. Restart & Test**

```bash
docker-compose restart api
docker-compose logs api | grep -i openai
```

### Pricing

- **GPT-4 Turbo**: $0.01 per 1K input tokens, $0.03 per 1K output tokens
- **Example**: Analyzing a 10K line project ≈ $0.50

### Available Models

```yaml
# Recommended
model: gpt-4-turbo-preview  # Latest, fastest GPT-4

# Other options
model: gpt-4                # Standard GPT-4
model: gpt-3.5-turbo        # Cheaper, faster, less accurate
```

---

## 3️⃣ Anthropic Claude

### Why Claude?
- ✅ **Long context**: 200K tokens (huge projects)
- ✅ **Accurate**: Very good at understanding code
- ✅ **Safe**: Strong refusal of harmful content
- ⚠️ **Paid only**: No free tier

### Step-by-Step Setup

**1. Get Your API Key**

Go to: https://console.anthropic.com/

- Sign up / Log in
- Go to "API Keys"
- Click "Create Key"
- Copy your key (starts with `sk-ant-...`)

**2. Configure the App**

```bash
export LLM_API_KEY="sk-ant-..."
export LLM_PROVIDER="anthropic"
export LLM_MODEL="claude-3-5-sonnet-20241022"
```

**3. Restart & Test**

```bash
docker-compose restart api
docker-compose logs api | grep -i anthropic
```

### Pricing

- **Claude 3.5 Sonnet**: $0.003 per 1K input tokens, $0.015 per 1K output tokens
- **Claude 3 Opus**: More expensive but best quality

### Available Models

```yaml
# Recommended (best balance)
model: claude-3-5-sonnet-20241022

# Other options
model: claude-3-opus-20240229     # Best quality, slower
model: claude-3-haiku-20240307    # Fastest, cheaper
```

---

## 4️⃣ AWS Bedrock

### Why Bedrock?
- ✅ **Enterprise**: AWS infrastructure
- ✅ **Multiple models**: Claude, Llama, etc.
- ✅ **Compliance**: HIPAA, SOC 2
- ⚠️ **Complex setup**: Need AWS account + permissions

### Step-by-Step Setup

**1. Enable Bedrock in AWS**

Go to: https://console.aws.amazon.com/bedrock/

- Select your region (us-east-1 recommended)
- Go to "Model access"
- Request access to "Claude 3 Sonnet"
- Wait for approval (~5 minutes)

**2. Create IAM User**

- Go to IAM → Users → Create user
- Attach policy: `AmazonBedrockFullAccess`
- Create access keys
- Copy: Access Key ID + Secret Access Key

**3. Configure the App**

```bash
export AWS_ACCESS_KEY_ID="AKIA..."
export AWS_SECRET_ACCESS_KEY="..."
export AWS_REGION="us-east-1"
export LLM_PROVIDER="bedrock"
export LLM_MODEL="anthropic.claude-3-sonnet-20240229-v1:0"
```

Or in `application.yml`:

```yaml
docgen:
  llm:
    provider: bedrock
    model: anthropic.claude-3-sonnet-20240229-v1:0
    aws-region: us-east-1
```

**4. Restart & Test**

```bash
docker-compose restart api
docker-compose logs api | grep -i bedrock
```

### Available Models

```yaml
# Claude models (recommended)
model: anthropic.claude-3-5-sonnet-20241022-v2:0  # Latest Sonnet
model: anthropic.claude-3-sonnet-20240229-v1:0    # Sonnet
model: anthropic.claude-3-haiku-20240307-v1:0     # Fast

# Other models
model: meta.llama3-70b-instruct-v1:0              # Llama 3
model: amazon.titan-text-express-v1               # Amazon Titan
```

### Pricing

Bedrock pricing varies by model and region. Check: https://aws.amazon.com/bedrock/pricing/

Example (us-east-1):
- Claude 3 Sonnet: $0.003/1K input tokens, $0.015/1K output tokens

---

## 🔧 Configuration Reference

### Environment Variables

```bash
# Required for all providers (except Mock and Bedrock)
export LLM_API_KEY="your-api-key"

# Required
export LLM_PROVIDER="gemini"  # gemini, openai, anthropic, bedrock, mock

# Optional
export LLM_MODEL="gemini-pro"  # Default model for the provider

# Bedrock only
export AWS_ACCESS_KEY_ID="AKIA..."
export AWS_SECRET_ACCESS_KEY="..."
export AWS_REGION="us-east-1"
```

### application.yml

```yaml
docgen:
  llm:
    enabled: true
    provider: gemini  # gemini, openai, anthropic, bedrock, mock
    api-key: ${LLM_API_KEY:}
    model: ${LLM_MODEL:gemini-pro}
    aws-region: ${AWS_REGION:us-east-1}
```

### Docker Compose

```yaml
services:
  api:
    environment:
      # Gemini
      - LLM_PROVIDER=gemini
      - LLM_API_KEY=AIza...

      # OpenAI
      # - LLM_PROVIDER=openai
      # - LLM_API_KEY=sk-proj-...

      # Anthropic
      # - LLM_PROVIDER=anthropic
      # - LLM_API_KEY=sk-ant-...

      # Bedrock
      # - LLM_PROVIDER=bedrock
      # - AWS_ACCESS_KEY_ID=AKIA...
      # - AWS_SECRET_ACCESS_KEY=...
      # - AWS_REGION=us-east-1
```

---

## 🧪 Testing Your Setup

### 1. Check Logs

```bash
docker-compose logs api | grep -i llm
```

You should see:
```
Using Google Gemini client with model: gemini-pro
```

### 2. Import a Small Project

```bash
curl -X POST http://localhost:8080/v1/projects/import \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-ai",
    "gitUrl": "https://github.com/expressjs/express"
  }'
```

### 3. Check Analysis Results

Wait 2-3 minutes, then:

```bash
curl http://localhost:8080/v1/projects/{project-id}/status
```

Look for AI-generated insights in the documentation.

---

## 🐛 Troubleshooting

### Error: "API key not configured"

```bash
# Check environment variable is set
echo $LLM_API_KEY

# Should output your key, not empty
```

### Error: "Invalid API key"

- **Gemini**: Check key starts with `AIza`
- **OpenAI**: Check key starts with `sk-proj-`
- **Anthropic**: Check key starts with `sk-ant-`

### Error: "Rate limit exceeded"

**Gemini Free Tier**:
- Limit: 60 requests/minute
- Solution: Wait 1 minute or upgrade to paid

**OpenAI**:
- Check your usage: https://platform.openai.com/usage
- Add payment method if needed

### Error: "AWS credentials not found"

```bash
# Check AWS credentials
aws configure list

# Or set explicitly
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."
```

### Error: "Model access denied" (Bedrock)

- Go to AWS Bedrock console
- Request model access
- Wait for approval (usually 5-10 minutes)

---

## 💡 Best Practices

### 1. Start with Gemini

For testing and small projects, use Gemini (free tier).

### 2. Use OpenAI for Production

Once you're happy, switch to OpenAI GPT-4 for best quality.

### 3. Use Bedrock for Enterprise

If you're on AWS, use Bedrock for compliance and integration.

### 4. Monitor Costs

- Set up billing alerts in OpenAI/Anthropic
- Use AWS Cost Explorer for Bedrock
- Start with small projects to estimate costs

### 5. Secure Your Keys

```bash
# Never commit API keys to Git!

# Use .env file (gitignored)
echo "LLM_API_KEY=sk-..." > .env

# Load in docker-compose
docker-compose --env-file .env up -d
```

---

## 📊 Performance Comparison

| Provider | Speed | Quality | Cost (10K project) | Free Tier |
|----------|-------|---------|-------------------|-----------|
| **Gemini** | ⚡⚡⚡ 2s | ⭐⭐⭐⭐ Good | $0 (free) | ✅ Yes |
| **GPT-4** | ⚡⚡ 4s | ⭐⭐⭐⭐⭐ Best | ~$0.50 | ❌ No |
| **Claude** | ⚡⚡ 3s | ⭐⭐⭐⭐⭐ Best | ~$0.40 | ❌ No |
| **Bedrock** | ⚡⚡ 3s | ⭐⭐⭐⭐ Good | ~$0.40 | ❌ No |
| **Mock** | ⚡⚡⚡⚡ 0s | ⭐⭐ Basic | $0 | ✅ Yes |

---

## 🎓 Next Steps

1. **Choose a provider** based on your needs
2. **Get API key** from the provider
3. **Configure** environment variables
4. **Restart** the app
5. **Test** with a small project
6. **Monitor** costs and quality

Need help? Check the [main README](../README_EN.md) or open an issue!

---

**Happy AI-powered documenting!** 🚀
