// server.js – Snickr Express application entry point
require('dotenv').config();
const express        = require('express');
const session        = require('express-session');
const pgSession      = require('connect-pg-simple')(session);
const path           = require('path');
const pool           = require('./db');

const authRoutes      = require('./routes/auth');
const workspaceRoutes = require('./routes/workspaces');
const channelRoutes   = require('./routes/channels');
const messageRoutes   = require('./routes/messages');
const inviteRoutes    = require('./routes/invitations');
const searchRoutes    = require('./routes/search');

const app = express();

// ── View engine ──────────────────────────────────────────────
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// ── Static assets ────────────────────────────────────────────
app.use(express.static(path.join(__dirname, 'public')));

// ── Body parsing ─────────────────────────────────────────────
app.use(express.urlencoded({ extended: true }));
app.use(express.json());

// ── Sessions (stored in Postgres) ────────────────────────────
app.use(session({
  store: new pgSession({
    pool,
    tableName: 'user_sessions',
    createTableIfMissing: true,
  }),
  secret:            process.env.SESSION_SECRET || 'dev_secret',
  resave:            false,
  saveUninitialized: false,
  cookie: {
    maxAge:   7 * 24 * 60 * 60 * 1000, // 1 week
    httpOnly: true,
    sameSite: 'lax',
  },
}));

// ── Locals available in every template ───────────────────────
app.use((req, res, next) => {
  res.locals.currentUser  = req.session.user || null;
  res.locals.flash        = req.session.flash || null;
  res.locals.pendingCount = 0;
  delete req.session.flash;
  next();
});

// ── Routes ───────────────────────────────────────────────────
app.use('/',            authRoutes);
app.use('/workspaces',  workspaceRoutes);
app.use('/channels',    channelRoutes);
app.use('/messages',    messageRoutes);
app.use('/invitations', inviteRoutes);
app.use('/search',      searchRoutes);

// ── Home redirect ─────────────────────────────────────────────
app.get('/', (req, res) => {
  if (req.session.user) return res.redirect('/workspaces');
  res.redirect('/login');
});

// ── 404 ───────────────────────────────────────────────────────
app.use((req, res) => {
  res.status(404).render('error', {
    message: 'Page not found', code: 404,
    currentUser: req.session?.user || null,
    flash: null,
  });
});

// ── Error handler ─────────────────────────────────────────────
app.use((err, req, res, _next) => {
  console.error(err);
  res.status(500).render('error', {
    message: 'Internal server error', code: 500,
    currentUser: req.session?.user || null,
    flash: null,
  });
});

// ── Start ─────────────────────────────────────────────────────
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Snickr running at http://localhost:${PORT}`);
});
