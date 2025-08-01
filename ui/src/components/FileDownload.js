import React, { useState } from 'react';

const FileDownload = ({ onDownload, isDownloading }) => {
  const [inviteCode, setInviteCode] = useState('');
  const [error, setError] = useState('');

  const handleDownloadClick = async () => {
    setError('');
    
    if (!inviteCode.trim()) {
      setError('Please enter a valid invite code.');
      return;
    }
    
    const port = parseInt(inviteCode.trim(), 10);
    if (isNaN(port) || port <= 0 || port > 65535) {
      setError('Please enter a valid port number (1-65535)');
      return;
    }
    
    try {
      await onDownload(port);
    } catch (err) {
      setError('Failed to download the file. Please check the invite code and try again.');
    }
  };

  return (
    <div className="file-download-container">
      <div className="download-header">
        <h2 className="download-title">
          <i className="fas fa-download"></i> Receive a File
        </h2>
        <p className="download-subtitle">
          Enter the invite code shared with you to download the file
        </p>
      </div>
      
      <div className="input-group">
        <label htmlFor="inviteCode" className="input-label">
          <i className="fas fa-key"></i> Invite Code
        </label>
        <input
          type="text"
          id="inviteCode"
          value={inviteCode}
          onChange={(e) => setInviteCode(e.target.value)}
          placeholder="Enter the invite code (port number)"
          className="input-field"
          disabled={isDownloading}
        />
        {error && <div className="error-message">{error}</div>}
      </div>
      
      <button
        onClick={handleDownloadClick}
        disabled={isDownloading}
        className="download-button"
      >
        <i className={`fas ${isDownloading ? 'fa-spinner fa-spin' : 'fa-download'}`}></i>
        {isDownloading ? 'Downloading...' : 'Download File'}
      </button>
    </div>
  );
};

export default FileDownload;