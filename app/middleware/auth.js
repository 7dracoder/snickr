// middleware/auth.js – protect routes that require login
function requireLogin(req, res, next) {
  if (req.session && req.session.user) return next();
  req.session.flash = { type: 'error', message: 'Please log in first.' };
  res.redirect('/login');
}

module.exports = { requireLogin };
