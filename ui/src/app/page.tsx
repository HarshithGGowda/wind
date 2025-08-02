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
  const [port, setPort] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<'upload' | 'download'>('upload');

  const handleFileUpload = async (file: File) => {
    setUploadedFile(file);
    setIsUploading(true);
    
    try {
      const formData = new FormData();
      formData.append('file', file);
      
      const response = await axios.post('/api/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      
      setPort(response.data.port);
    } catch (error) {
      console.error('Error uploading file:', error);
      alert('Failed to upload file. Please try again.');
    } finally {
      setIsUploading(false);
    }
  };
  
  const handleDownload = async (port: number) => {
    setIsDownloading(true);
    
    try {
      // Request download from Java backend
      const response = await axios.get(`/api/download/${port}`, {
        responseType: 'blob',
      });
      
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      
      // Try to get filename from response headers
      // Axios normalizes headers to lowercase, but we need to handle different cases
      const headers = response.headers;
      let contentDisposition = '';
      
      // Look for content-disposition header regardless of case
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
    } catch (error) {
      console.error('Error downloading file:', error);
      alert('Failed to download file. Please check the invite code and try again.');
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
                      <span className="text-slate-500 ml-2">({Math.round(uploadedFile.size / 1024)} KB)</span>
                    </p>
                  </div>
                )}
                
                {isUploading && (
                  <div className="text-center py-8">
                    <div className="inline-block animate-spin rounded-full h-10 w-10 border-4 border-blue-500 border-t-transparent"></div>
                    <p className="mt-4 text-slate-600 font-medium">Uploading file...</p>
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
                    <p className="mt-4 text-slate-600 font-medium">Downloading file...</p>
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
