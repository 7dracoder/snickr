// routes/auth.js
const express   = require('express');
const bcrypt    = require('bcrypt');
const rateLimit = require('express-rate-limit');
const { body, validationResult } = require('express-validator');
const pool      = require('../db');
const router    = express.Router();

const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20,
  message: 'Too many login attempts. Try again in 15 minutes.',
  standardHeaders: true,
  legacyHeaders: false,
});

// ── GET /register
router.get('/register', (req, res) => {
  if (req.session.user) return res.redirect('/workspaces');
  res.render('auth/register', { errors: [] });
});

// ── POST /register
router.post('/register', [
  body('email').isEmail().normalizeEmail().withMessage('Valid email required'),
  body('username').trim().isLength({ min: 3, max: 30 })
    .matches(/^[a-zA-Z0-9_]+$/).withMessage('Username: 3–30 chars, letters/numbers/_'),
  body('nickname').trim().isLength({ max: 50 }).optional({ checkFalsy: true }),
  body('password').isLength({ min: 6 }).withMessage('Password must be at least 6 characters'),
  body('confirm_password').custom((val, { req }) => {
    if (val !== req.body.password) throw new Error('Passwords do not match');
    return true;
  }),
], async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.render('auth/register', { errors: errors.array() });

  const { email, username, nickname, password } = req.body;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const hash = await bcrypt.hash(password, 12);
    const { rows: [user] } = await client.query(
      `INSERT INTO users (email, username, nickname, password)
       VALUES ($1, $2, $3, $4) RETURNING user_id, username, email, nickname`,
      [email, username, nickname || null, hash]
    );

    // Auto-link any pending workspace invitations sent to this email
    await client.query(
      `UPDATE workspace_invitations
       SET invitee_id = $1
       WHERE invitee_email = $2 AND status = 'pending' AND invitee_id IS NULL`,
      [user.user_id, email]
    );

    await client.query('COMMIT');
    req.session.user = user;
    req.session.flash = { type: 'success', message: `Welcome to Snickr, ${username}!` };
    res.redirect('/workspaces');
  } catch (err) {
    await client.query('ROLLBACK');
    if (err.code === '23505') {
      const field = err.detail && err.detail.includes('email') ? 'Email' : 'Username';
      return res.render('auth/register', { errors: [{ msg: `${field} is already taken.` }] });
    }
    console.error(err);
    res.render('auth/register', { errors: [{ msg: 'Registration failed. Try again.' }] });
  } finally {
    client.release();
  }
});

// ── GET /login
router.get('/login', (req, res) => {
  if (req.session.user) return res.redirect('/workspaces');
  res.render('auth/login', { errors: [] });
});

// ── POST /login
router.post('/login', loginLimiter, [
  body('username').trim().notEmpty().withMessage('Username required'),
  body('password').notEmpty().withMessage('Password required'),
], async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.render('auth/login', { errors: errors.array() });

  const { username, password } = req.body;
  try {
    const { rows: [user] } = await pool.query(
      'SELECT * FROM users WHERE username = $1', [username]
    );
    if (!user || !(await bcrypt.compare(password, user.password))) {
      return res.render('auth/login', { errors: [{ msg: 'Invalid username or password.' }] });
    }
    req.session.regenerate((err) => {
      if (err) throw err;
      req.session.user = {
        user_id:  user.user_id,
        username: user.username,
        email:    user.email,
        nickname: user.nickname,
      };
      req.session.flash = { type: 'success', message: `Welcome back, ${user.username}!` };
      res.redirect('/workspaces');
    });
  } catch (err) {
    console.error(err);
    res.render('auth/login', { errors: [{ msg: 'Login failed. Try again.' }] });
  }
});

// ── POST /logout
router.post('/logout', (req, res) => {
  req.session.destroy(() => res.redirect('/login'));
});

module.exports = router;
