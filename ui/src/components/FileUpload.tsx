'use client';

import { useState, useCallback } from 'react';
import { useDropzone } from 'react-dropzone';
import { FiUpload, FiFile } from 'react-icons/fi';

interface FileUploadProps {
  onFileUpload: (file: File) => void;
  isUploading: boolean;
}

export default function FileUpload({ onFileUpload, isUploading }: FileUploadProps) {
  const [dragActive, setDragActive] = useState(false);
  
  const onDrop = useCallback((acceptedFiles: File[]) => {
    if (acceptedFiles.length > 0) {
      onFileUpload(acceptedFiles[0]);
    }
  }, [onFileUpload]);
  
  const { getRootProps, getInputProps } = useDropzone({ 
    onDrop,
    multiple: false,
    onDragEnter: () => setDragActive(true),
    onDragLeave: () => setDragActive(false),
    onDropAccepted: () => setDragActive(false),
    onDropRejected: () => setDragActive(false),
  });

  return (
    <div 
      {...getRootProps()} 
      className={`
        relative w-full p-12 border-2 border-dashed rounded-2xl text-center cursor-pointer transition-all duration-300 transform
        ${dragActive 
          ? 'border-blue-500 bg-gradient-to-br from-blue-50 to-purple-50 scale-[1.02] shadow-lg' 
          : 'border-slate-300 hover:border-blue-400 hover:bg-gradient-to-br hover:from-slate-50 hover:to-blue-50 hover:scale-[1.01] hover:shadow-md'
        }
        ${isUploading ? 'opacity-50 pointer-events-none' : ''}
      `}
    >
      <input {...getInputProps()} />
      <div className="flex flex-col items-center justify-center space-y-6">
        <div className={`p-6 rounded-full transition-all duration-300 ${
          dragActive 
            ? 'bg-gradient-to-r from-blue-500 to-purple-500 text-white shadow-lg' 
            : 'bg-gradient-to-r from-blue-100 to-purple-100 text-blue-600'
        }`}>
          <FiUpload className="w-8 h-8" />
        </div>
        <div className="space-y-2">
          <p className="text-2xl font-bold text-slate-800">Drag & drop a file here</p>
          <p className="text-lg text-slate-600">or click to select</p>
        </div>
        <div className="flex items-center space-x-2 px-4 py-2 bg-white/50 rounded-full border border-slate-200">
          <FiFile className="w-4 h-4 text-slate-500" />
          <p className="text-sm text-slate-600 font-medium">
            Share any file with your peers securely
          </p>
        </div>
      </div>
    </div>
  );
}
