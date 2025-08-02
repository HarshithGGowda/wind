'use client';

import { useState } from 'react';
import { FiDownload, FiHash } from 'react-icons/fi';

interface FileDownloadProps {
  onDownload: (port: number) => Promise<void>;
  isDownloading: boolean;
}

export default function FileDownload({ onDownload, isDownloading }: FileDownloadProps) {
  const [inviteCode, setInviteCode] = useState('');
  const [error, setError] = useState('');
  
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    
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
    <div className="space-y-6">
      <div className="bg-gradient-to-r from-blue-50 via-purple-50 to-indigo-50 p-6 rounded-2xl border border-blue-200/50 shadow-sm">
        <div className="flex items-center space-x-3 mb-3">
          <div className="p-2 bg-white rounded-lg shadow-sm">
            <FiDownload className="w-5 h-5 text-blue-600" />
          </div>
          <h3 className="text-xl font-bold text-blue-900">Receive a File</h3>
        </div>
        <p className="text-blue-700 leading-relaxed">
          Enter the invite code shared with you to download the file securely.
        </p>
      </div>
      
      <form onSubmit={handleSubmit} className="space-y-6">
        <div className="space-y-3">
          <label htmlFor="inviteCode" className="flex items-center space-x-2 text-sm font-semibold text-slate-700">
            <FiHash className="w-4 h-4" />
            <span>Invite Code</span>
          </label>
          <input
            type="text"
            id="inviteCode"
            value={inviteCode}
            onChange={(e) => setInviteCode(e.target.value)}
            placeholder="Enter the invite code (port number)"
            className="input-field text-lg font-mono"
            disabled={isDownloading}
            required
          />
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg">
              <p className="text-sm text-red-700 font-medium">{error}</p>
            </div>
          )}
        </div>
        
        <button
          type="submit"
          className="btn-primary flex items-center justify-center w-full text-lg py-4"
          disabled={isDownloading}
        >
          {isDownloading ? (
            <span className="flex items-center space-x-2">
              <div className="animate-spin rounded-full h-5 w-5 border-2 border-white border-t-transparent"></div>
              <span>Downloading...</span>
            </span>
          ) : (
            <>
              <FiDownload className="mr-3 w-5 h-5" />
              <span>Download File</span>
            </>
          )}
        </button>
      </form>
    </div>
  );
}
