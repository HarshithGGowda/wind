import React, { useState } from 'react';

const FileUpload = ({ onFileUpload, isUploading }) => {
  const [file, setFile] = useState(null);
  const [dragActive, setDragActive] = useState(false);

  const handleFileChange = (event) => {
    setFile(event.target.files[0]);
  };

  const handleDrag = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      setFile(e.dataTransfer.files[0]);
    }
  };

  const handleUpload = () => {
    if (file) {
      onFileUpload(file);
    }
  };

  return (
    <div className="file-upload-container">
      <div
        className={`file-upload-area ${dragActive ? 'drag-active' : ''} ${isUploading ? 'disabled' : ''}`}
        onDragEnter={handleDrag}
        onDragLeave={handleDrag}
        onDragOver={handleDrag}
        onDrop={handleDrop}
        onClick={() => !isUploading && document.getElementById('file-input').click()}
      >
        <div className="upload-icon">
          <i className="fas fa-cloud-upload-alt"></i>
        </div>
        <p className="upload-text">
          {file ? file.name : 'Drag & drop a file here, or click to select'}
        </p>
        <p className="upload-subtext">
          Share any file with your peers securely
        </p>
      </div>
      
      <input
        id="file-input"
        type="file"
        onChange={handleFileChange}
        className="file-input"
        disabled={isUploading}
      />
      
      {file && (
        <button
          onClick={handleUpload}
          className={`upload-button ${isUploading ? 'disabled' : ''}`}
          disabled={isUploading}
        >
          <i className={`fas ${isUploading ? 'fa-spinner fa-spin' : 'fa-upload'}`}></i>
          {isUploading ? 'Uploading...' : 'Upload File'}
        </button>
      )}
    </div>
  );
};

export default FileUpload;