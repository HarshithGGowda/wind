'use client';

import { useState } from 'react';
import FileUpload from '@/components/FileUpload';
import FileDownload from '@/components/FileDownload';
import InviteCode from '@/components/InviteCode';
import axios from 'axios';

export default function Home() {
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [downloadProgress, setDownloadProgress] = useState(0);
  const [port, setPort] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<'upload' | 'download'>('upload');

  const handleFileUpload = async (file: File) => {
    // Check file size (500MB limit)
    const maxSize = 500 * 1024 * 1024; // 500MB
    if (file.size > maxSize) {
      alert('File size exceeds 500MB limit. Please choose a smaller file.');
      return;
    }

    setUploadedFile(file);
    setIsUploading(true);
    setUploadProgress(0);
    
    try {
      const formData = new FormData();
      formData.append('file', file);
      
      const response = await axios.post('/api/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
        timeout: 300000, // 5 minute timeout
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
            setUploadProgress(progress);
          }
        },
      });
      
      setPort(response.data.port);
      setUploadProgress(100);
    } catch (error: any) {
      console.error('Error uploading file:', error);
      if (error.code === 'ECONNABORTED') {
        alert('Upload timeout. Please try with a smaller file or check your connection.');
      } else if (error.response?.status === 413) {
        alert('File too large. Please choose a smaller file.');
      } else {
        alert('Failed to upload file. Please try again.');
      }
    } finally {
      setIsUploading(false);
    }
  };
  
  const handleDownload = async (port: number) => {
    setIsDownloading(true);
    setDownloadProgress(0);
    
    try {
      const response = await axios.get(`/api/download/${port}`, {
        responseType: 'blob',
        timeout: 300000, // 5 minute timeout
        onDownloadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
            setDownloadProgress(progress);
          }
        },
      });
      
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      
      const headers = response.headers;
      let contentDisposition = '';
      
      for (const key in headers) {
        if (key.toLowerCase() === 'content-disposition') {
          contentDisposition = headers[key];
          break;
        }
      }
      
      let filename = 'downloaded-file';
      
      if (contentDisposition) {
        const filenameMatch = contentDisposition.match(/filename="(.+)"/);
        if (filenameMatch && filenameMatch.length === 2) {
          filename = filenameMatch[1];
        }
      }
      
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      setDownloadProgress(100);
      
    } catch (error: any) {
      console.error('Error downloading file:', error);
      if (error.code === 'ECONNABORTED') {
        alert('Download timeout. Please try again or check your connection.');
      } else {
        alert('Failed to download file. Please check the invite code and try again.');
      }
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col">
      {/* Header */}
      <header className="bg-white/80 backdrop-blur-sm shadow-sm border-b border-white/20">
        <div className="container mx-auto px-6 py-6">
          <div className="text-center">
            <h1 className="text-5xl font-bold bg-gradient-to-r from-blue-600 via-purple-600 to-indigo-600 bg-clip-text text-transparent mb-3">
              Wind
            </h1>
            <p className="text-xl text-slate-600 font-medium">Secure P2P File Sharing</p>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <div className="flex-1 container mx-auto px-6 py-12 max-w-4xl">
        <div className="bg-white/70 backdrop-blur-sm rounded-2xl shadow-xl border border-white/20 overflow-hidden">
          {/* Tab Navigation */}
          <div className="bg-gradient-to-r from-slate-50 to-blue-50/50 border-b border-slate-200/50">
            <div className="flex">
              <button
                className={`flex-1 px-8 py-6 font-semibold text-lg transition-all duration-200 ${
                  activeTab === 'upload'
                    ? 'text-blue-600 bg-white/80 border-b-3 border-blue-500 shadow-sm'
                    : 'text-slate-600 hover:text-slate-800 hover:bg-white/40'
                }`}
                onClick={() => setActiveTab('upload')}
              >
                Share a File
              </button>
              <button
                className={`flex-1 px-8 py-6 font-semibold text-lg transition-all duration-200 ${
                  activeTab === 'download'
                    ? 'text-blue-600 bg-white/80 border-b-3 border-blue-500 shadow-sm'
                    : 'text-slate-600 hover:text-slate-800 hover:bg-white/40'
                }`}
                onClick={() => setActiveTab('download')}
              >
                Receive a File
              </button>
            </div>
          </div>
          
          {/* Tab Content */}
          <div className="p-8">
            {activeTab === 'upload' ? (
              <div className="space-y-6">
                <FileUpload onFileUpload={handleFileUpload} isUploading={isUploading} />
                
                {uploadedFile && !isUploading && (
                  <div className="p-4 bg-gradient-to-r from-slate-50 to-blue-50 rounded-xl border border-slate-200/50">
                    <p className="text-sm text-slate-700">
                      Selected file: <span className="font-semibold text-slate-900">{uploadedFile.name}</span> 
                      <span className="text-slate-500 ml-2">({(uploadedFile.size / 1024 / 1024).toFixed(2)} MB)</span>
                    </p>
                  </div>
                )}
                
                {isUploading && (
                  <div className="text-center py-8">
                    <div className="inline-block animate-spin rounded-full h-10 w-10 border-4 border-blue-500 border-t-transparent"></div>
                    <p className="mt-4 text-slate-600 font-medium">Uploading file... {uploadProgress}%</p>
                    <div className="w-full bg-gray-200 rounded-full h-2 mt-4">
                      <div 
                        className="bg-blue-600 h-2 rounded-full transition-all duration-300" 
                        style={{ width: `${uploadProgress}%` }}
                      ></div>
                    </div>
                  </div>
                )}
                
                <InviteCode port={port} />
              </div>
            ) : (
              <div className="space-y-6">
                <FileDownload onDownload={handleDownload} isDownloading={isDownloading} />
                
                {isDownloading && (
                  <div className="text-center py-8">
                    <div className="inline-block animate-spin rounded-full h-10 w-10 border-4 border-blue-500 border-t-transparent"></div>
                    <p className="mt-4 text-slate-600 font-medium">Downloading file... {downloadProgress}%</p>
                    <div className="w-full bg-gray-200 rounded-full h-2 mt-4">
                      <div 
                        className="bg-blue-600 h-2 rounded-full transition-all duration-300" 
                        style={{ width: `${downloadProgress}%` }}
                      ></div>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
      
      {/* Footer */}
      <footer className="bg-white/60 backdrop-blur-sm border-t border-white/20 py-8">
        <div className="container mx-auto px-6 text-center">
          <p className="text-slate-500 font-medium">Wind &copy; {new Date().getFullYear()} - Secure P2P File Sharing</p>
        </div>
      </footer>
    </div>
  );
}
