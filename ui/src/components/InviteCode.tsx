'use client';

import { useState } from 'react';
import { FiCopy, FiCheck, FiShare2, FiWifi } from 'react-icons/fi';

interface InviteCodeProps {
  port: number | null;
}

export default function InviteCode({ port }: InviteCodeProps) {
  const [copied, setCopied] = useState(false);
  
  if (!port) return null;
  
  const copyToClipboard = () => {
    navigator.clipboard.writeText(port.toString());
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  
  return (
    <div className="p-6 bg-gradient-to-r from-green-50 via-emerald-50 to-teal-50 border border-green-200/50 rounded-2xl shadow-sm">
      <div className="flex items-center space-x-3 mb-4">
        <div className="p-2 bg-white rounded-lg shadow-sm">
          <FiShare2 className="w-5 h-5 text-green-600" />
        </div>
        <h3 className="text-xl font-bold text-green-900">File Ready to Share!</h3>
      </div>
      
      <p className="text-green-700 mb-6 leading-relaxed">
        Share this invite code with anyone you want to share the file with:
      </p>
      
      <div className="flex items-center mb-6">
        <div className="flex-1 bg-white p-4 rounded-l-xl border border-r-0 border-gray-300 shadow-sm">
          <div className="flex items-center space-x-3">
            <FiWifi className="w-5 h-5 text-slate-500" />
            <span className="font-mono text-2xl font-bold text-slate-800">{port}</span>
          </div>
        </div>
        <button
          onClick={copyToClipboard}
          className={`p-4 rounded-r-xl transition-all duration-200 shadow-sm ${
            copied 
              ? 'bg-green-500 text-white' 
              : 'bg-blue-600 hover:bg-blue-700 text-white'
          }`}
          aria-label="Copy invite code"
        >
          {copied ? <FiCheck className="w-6 h-6" /> : <FiCopy className="w-6 h-6" />}
        </button>
      </div>
      
      <div className="flex items-center space-x-2 p-3 bg-white/50 rounded-lg border border-green-200/50">
        <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
        <p className="text-xs text-slate-600 font-medium">
          This code will be valid as long as your file sharing session is active.
        </p>
      </div>
    </div>
  );
}
