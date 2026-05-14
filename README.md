# Snickr

A Slack-like messaging web app — CS6083 Project 2, NYU Spring 2026.

## How to run locally

### Prerequisites

- [Node.js](https://nodejs.org/) v18 or higher
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (must be running)

### Steps

**1. Clone the repo**

```bash
git clone <repo-url>
cd snickr
git checkout TS
```

**2. Start Docker Desktop**

Open Docker Desktop from your Start menu and wait until it shows the engine is running.

**3. Start the database**

```bash
docker compose up -d
```

This starts a PostgreSQL container on port 5433, creates the schema, and loads sample data automatically.

**4. Install dependencies**

```bash
cd app
npm install
```

**5. Configure environment**

Copy the example env file:

```bash
copy .env.example .env
```

The defaults work out of the box with the Docker setup. No changes needed.

**6. Start the server**

```bash
node server.js
```

**7. Open the app**

Go to [http://localhost:3000](http://localhost:3000) in your browser.

Register a new account to get started, or use the sample data already loaded.

---

### Sample data

The database comes pre-loaded with:

- 2 workspaces: **TechCorp** and **BookClub**
- Channels: `#general`, `#engineering`, `#hiring`, `#reads`
- 5 users and sample messages

Register your own account to explore the app.

---

### Stopping

```bash
# Stop the Node server: Ctrl+C

# Stop the database
docker compose down
```

To fully reset the database (wipe all data):

```bash
docker compose down -v
docker compose up -d
```
