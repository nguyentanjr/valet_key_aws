import React, { useState } from 'react';
import { FaCloudUploadAlt } from 'react-icons/fa';
import { fileAPI } from '../services/api';
import './FileUpload.css';

function FileUpload({ currentFolderId, onUploadSuccess }) {
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (file) {
      setSelectedFile(file);
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) return;

    setUploading(true);
    setProgress(0);

    try {
      // Step 1: Get presigned upload URL from backend
      const urlResponse = await fileAPI.generateUploadUrl(
        selectedFile.name,
        selectedFile.size,
        currentFolderId
      );
      
      const { uploadUrl, fileId } = urlResponse.data;
      setProgress(10);

      // Step 2: Upload directly to S3 using presigned URL
      const uploadResponse = await fileAPI.uploadToS3(selectedFile, uploadUrl);
      
      if (!uploadResponse.ok) {
        throw new Error('Failed to upload to S3');
      }

      setProgress(80);

      // Step 3: Confirm upload with backend
      await fileAPI.confirmUpload(fileId, selectedFile.type);
      
      setProgress(100);
      setTimeout(() => {
        setSelectedFile(null);
        setUploading(false);
        setProgress(0);
        onUploadSuccess();
        document.getElementById('file-input').value = '';
      }, 500);
    } catch (err) {
      alert(err.response?.data?.message || err.message || 'Upload failed');
      setUploading(false);
      setProgress(0);
    }
  };

  return (
    <div className="file-upload-container">
      <div className="upload-area">
        <FaCloudUploadAlt className="upload-icon" />
        <h3>Upload Files</h3>
        <p>Select a file to upload to your cloud storage</p>
        
        <input
          id="file-input"
          type="file"
          onChange={handleFileSelect}
          disabled={uploading}
          style={{ display: 'none' }}
        />
        
        <label htmlFor="file-input" className="btn btn-primary">
          Choose File
        </label>

        {selectedFile && (
          <div className="selected-file">
            <p><strong>Selected:</strong> {selectedFile.name}</p>
            <p><strong>Size:</strong> {(selectedFile.size / 1024 / 1024).toFixed(2)} MB</p>
            
            <button
              className="btn btn-success"
              onClick={handleUpload}
              disabled={uploading}
            >
              {uploading ? `Uploading... ${Math.round(progress)}%` : 'Upload'}
            </button>
          </div>
        )}

        {uploading && (
          <div className="progress-bar">
            <div className="progress-fill" style={{ width: `${progress}%` }}></div>
          </div>
        )}
      </div>
    </div>
  );
}

export default FileUpload;

