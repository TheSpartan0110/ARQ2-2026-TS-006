import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { LoginPage } from '../pages/LoginPage'
import { ProfilePage } from '../pages/ProfilePage'
import { JwksViewerPage } from '../pages/JwksViewerPage'
import { CallbackPage } from '../pages/CallbackPage'

export const AppRoutes: React.FC = () => {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/callback" element={<CallbackPage />} />
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/jwks" element={<JwksViewerPage />} />
      <Route path="*" element={<Navigate to="/profile" replace />} />
    </Routes>
  )
}

