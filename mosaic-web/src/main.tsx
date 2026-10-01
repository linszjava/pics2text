import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { LayoutEditorPage } from './LayoutEditorPage';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode><LayoutEditorPage /></StrictMode>,
);
