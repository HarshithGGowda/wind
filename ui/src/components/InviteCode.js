import React, { useState } from 'react';

const InviteCode = ({ port }) => {
  const [copied, setCopied] = useState(false);

  if (!port) return null;

  const copyToClipboard = () => {
    navigator.clipboard.writeText(port.toString()).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div className="invite-code-container">
      <h2 className="invite-title">
        <i className="fas fa-check-circle"></i> File Ready to Share!
      </h2>
      <p className="invite-subtitle">
        Share this invite code with anyone you want to share the file with:
      </p>
      
      <div className="invite-code-display">
        <input
          type="text"
          value={port}
          readOnly
          className="invite-code"
        />
        <button
          onClick={copyToClipboard}
          className={`copy-button ${copied ? 'copied' : ''}`}
          aria-label="Copy invite code"
        >
          <i className={`fas ${copied ? 'fa-check' : 'fa-copy'}`}></i>
        </button>
      </div>
      
      <p className="invite-note">
        <i className="fas fa-info-circle"></i> This code will be valid as long as your file sharing session is active.
      </p>
    </div>
  );
};

export default InviteCode;