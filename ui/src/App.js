import React, { useState } from 'react';
import FileUpload from './components/FileUpload';
import FileDownload from './components/FileDownload';
import InviteCode from './components/InviteCode';
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
    
    // Check file size (500MB limit)
    const maxSize = 500 * 1024 * 1024; // 500MB in bytes
    if (file.size > maxSize) {
      alert('File size exceeds 500MB limit. Please choose a smaller file.');
      setIsUploading(false);
      setUploadedFile(null);
      return;
    }
    
    try {
      const xhr = new XMLHttpRequest();
      const formData = new FormData();
      formData.append('file', file);
      
      xhr.upload.addEventListener('progress', (event) => {
        if (event.lengthComputable) {
          const percentComplete = (event.loaded / event.total) * 100;
          console.log(`Upload progress: ${percentComplete.toFixed(1)}%`);
          // You could add a progress bar here
        }
      });
      
      xhr.onload = function() {
        if (xhr.status === 200) {
          const data = JSON.parse(xhr.responseText);
          setPort(data.port);
        } else {
          throw new Error(`HTTP error! status: ${xhr.status}`);
        }
      };
      
      xhr.onerror = function() {
        throw new Error('Network error occurred');
      };
      
      xhr.open('POST', '/api/upload');
      xhr.send(formData);
    } catch (error) {
      console.error('Error uploading file:', error);
      alert('Failed to upload file. Please try again.');
      setUploadedFile(null);
    } finally {
      setIsUploading(false);
    }
  };
  
  const handleDownload = async (port) => {
    setIsDownloading(true);
    
    try {
      const response = await fetch(`/api/download/${port}`);
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const blob = await response.blob();
      
      // Extract filename from Content-Disposition header - FIXED
      const contentDisposition = response.headers.get('Content-Disposition');
      let filename = 'downloaded-file';
      
      if (contentDisposition) {
        // Try filename* first (RFC 6266)
        let filenameMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/);
        if (filenameMatch && filenameMatch[1]) {
          filename = decodeURIComponent(filenameMatch[1]);
        } else {
          // Fallback to regular filename
          filenameMatch = contentDisposition.match(/filename="([^"]+)"/);
          if (filenameMatch && filenameMatch[1]) {
            filename = filenameMatch[1];
          }
        }
      }
      
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Error downloading file:', error);
      alert('Failed to download file. Please check the invite code and try again.');
    } finally {
      setIsDownloading(false);
    }
  };

  const formatFileSize = (bytes) => {
    if (bytes >= 1024 * 1024) {
      return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    } else {
      return `${Math.round(bytes / 1024)} KB`;
    }
  };

  return (
    <div className="container">
      <header className="header">
        <h1 className="title">
          <i className="fas fa-wind"></i> Wind
        </h1>
        <p className="subtitle">End 2 End File Sharing</p>
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
                  <i className="fas fa-file"></i> Selected file: <strong>{uploadedFile.name}</strong> ({formatFileSize(uploadedFile.size)})
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