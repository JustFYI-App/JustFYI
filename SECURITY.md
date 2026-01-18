# Security Policy

## Reporting a Vulnerability

We take security seriously, especially given the sensitive nature of health-related data in this application.

If you discover a security vulnerability, please report it responsibly:

1. **Do NOT open a public GitHub issue** for security vulnerabilities
2. Email us at: **support@justfyi.app** (subject: "Security Vulnerability")
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Any suggested fixes (optional)

.  to provide a fix within 7 days for critical issues.

## Security Measures

### Data Protection

- **No personal data collected**: Users are identified only by random anonymous IDs
- **End-to-end privacy**: No names, emails, phone numbers, or identifying information stored
- **Local encryption**: SQLCipher encrypts all local database storage
- **Domain-separated hashing**: Different hash domains prevent cross-collection correlation

### Authentication

- **Anonymous authentication**: Firebase Anonymous Auth protects user privacy
- **No account linking**: Anonymous accounts cannot be linked to personal identities

### Backend Security

- **Firestore Security Rules**: Server-side validation of all database operations
- **Server-side hash computation**: Prevents client-side hash manipulation
- **Rate limiting**: Protection against brute-force attacks on sensitive endpoints
- **GDPR compliance**: EU region hosting, data export, and deletion capabilities

### Code Security

- **No hardcoded secrets**: All sensitive configuration uses environment variables
- **Dependency scanning**: Regular updates to address known vulnerabilities
- **Code review**: All changes reviewed before merging

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.9.x   | :white_check_mark: |

## Security Updates

Security updates will be released as soon as possible after a vulnerability is confirmed. Users are encouraged to always run the latest version of the app.
