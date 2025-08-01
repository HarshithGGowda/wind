import React, { useState } from 'react';
import FileUpload from '../components/FileUpload';
import FileDownload from '../components/FileDownload';
import InviteCode from '../components/InviteCode';
import './App.css';

function App() {
  const [uploadedFile, setUploadedFile] = useState(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [port, setPort] = useState(null);
  const [activeTab, setActiveTab] = useState('upload');

  const handleFileUpload = async (file) => {
    setUploadedFile(file);
    setIsUploading(true);
    
    try {
      const formData = new FormData();
      formData.append('file', file);
      
      const response = await fetch('/api/upload', {
        method: 'POST',
        body: formData,
      });
      
      const data = await response.json();
      setPort(data.port);
    } catch (error) {
      console.error('Error uploading file:', error);
      alert('Failed to upload file. Please try again.');
    } finally {
      setIsUploading(false);
    }
  };
  
  const handleDownload = async (port) => {
    setIsDownloading(true);
    
    try {
      const response = await fetch(`/api/download/${port}`);
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'downloaded-file');
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      console.error('Error downloading file:', error);
      alert('Failed to download file. Please check the invite code and try again.');
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div className="container">
      <header className="header">
        <h1 className="title">
          <i className="fas fa-wind"></i> Wind
        </h1>
        <p className="subtitle">Secure P2P File Sharing</p>
      </header>
      
      <div className="main-content">
        <div className="tabs">
          <button
            className={`tab ${activeTab === 'upload' ? 'active' : ''}`}
            onClick={() => setActiveTab('upload')}
          >
            <i className="fas fa-upload"></i> Share a File
          </button>
          <button
            className={`tab ${activeTab === 'download' ? 'active' : ''}`}
            onClick={() => setActiveTab('download')}
          >
            <i className="fas fa-download"></i> Receive a File
          </button>
        </div>
        
        {activeTab === 'upload' ? (
          <div className="upload-section">
            <FileUpload onFileUpload={handleFileUpload} isUploading={isUploading} />
            
            {uploadedFile && !isUploading && (
              <div className="file-info">
                <p>
                  <i className="fas fa-file"></i> Selected file: <strong>{uploadedFile.name}</strong> ({Math.round(uploadedFile.size / 1024)} KB)
                </p>
              </div>
            )}
            
            {isUploading && (
              <div className="loading">
                <div className="spinner"></div>
                <p>Uploading file...</p>
              </div>
            )}
            
            <InviteCode port={port} />
          </div>
        ) : (
          <div className="download-section">
            <FileDownload onDownload={handleDownload} isDownloading={isDownloading} />
            
            {isDownloading && (
              <div className="loading">
                <div className="spinner"></div>
                <p>Downloading file...</p>
              </div>
            )}
          </div>
        )}
      </div>
      
      <footer className="footer">
        <p><i className="fas fa-wind"></i> Wind  - Secure End to End File Sharing Platform</p>
      </footer>
    </div>
  );
}

export default App;