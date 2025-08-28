import {
  Component,
  Output,
  EventEmitter,
  signal,
  inject,
  ViewChild,
  ElementRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { FileUploadService } from '../../../core/services/file-upload.service';
import { LoggerService } from '../../../core/services/logger.service';

@Component({
  selector: 'app-file-upload',
  standalone: true,
  imports: [CommonModule, CardModule, MessageModule],
  templateUrl: './file-upload.component.html',
  styleUrl: './file-upload.component.css',
})
export class FileUploadComponent {
  @Output() fileUploaded = new EventEmitter<File>();
  @Output() filesUploaded = new EventEmitter<File[]>();
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  uploadedFile = signal<File | null>(null);
  uploadedFiles = signal<File[]>([]);
  errorMessage = signal<string>('');
  isDragOver = signal<boolean>(false);
  fileAnalyzed = signal<boolean>(false);
  processingFiles = signal<boolean>(false);

  private fileUploadService = inject(FileUploadService);
  private http = inject(HttpClient);
  private logger = inject(LoggerService);

  // Public methods
  getAcceptedFileTypes(): string {
    return this.fileUploadService.getAcceptedFileTypes();
  }

  useTemplate(type: 'sql' | 'csv', event?: Event): void {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }

    const fileName = type === 'sql' ? 'employees.sql' : 'employees.csv';
    const templatePath = `/templates/${fileName}`;

    // Load the actual template file
    this.http.get(templatePath, { responseType: 'text' }).subscribe({
      next: (content) => {
        const file = new File([content], fileName, {
          type: type === 'sql' ? 'application/sql' : 'text/csv',
        });
        this.handleFileSelection(file);
      },
      error: (error) => {
        this.logger.error('Error loading template', error, 'FileUploadComponent');
        this.errorMessage.set(`Failed to load template: ${fileName}`);
      }
    });
  }

  onFileInputChange(event: Event): void {
    this.logger.info('[FILE-UPLOAD-COMPONENT] File input change event', undefined, 'FileUploadComponent');
    const target = event.target as HTMLInputElement;
    const files = target.files;
    this.logger.debug('[FILE-UPLOAD-COMPONENT] Files from input', files, 'FileUploadComponent');
    if (files && files.length > 0) {
      if (files.length === 1) {
        this.logger.info('[FILE-UPLOAD-COMPONENT] Processing single file', { name: files[0].name }, 'FileUploadComponent');
        this.handleFileSelection(files[0]);
      } else {
        this.logger.info('[FILE-UPLOAD-COMPONENT] Processing multiple files', { count: files.length }, 'FileUploadComponent');
        this.handleMultipleFileSelection(Array.from(files));
      }
    }
  }

  onDrop(event: DragEvent): void {
    this.logger.info('[FILE-UPLOAD-COMPONENT] Drop event', undefined, 'FileUploadComponent');
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(false);

    const files = event.dataTransfer?.files;
    this.logger.debug('[FILE-UPLOAD-COMPONENT] Files from drop', files, 'FileUploadComponent');
    if (files && files.length > 0) {
      if (files.length === 1) {
        this.logger.info('[FILE-UPLOAD-COMPONENT] Processing dropped file', { name: files[0].name }, 'FileUploadComponent');
        this.handleFileSelection(files[0]);
      } else {
        this.logger.info('[FILE-UPLOAD-COMPONENT] Processing dropped files', { count: files.length }, 'FileUploadComponent');
        this.handleMultipleFileSelection(Array.from(files));
      }
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver.set(false);
  }

  onClear(): void {
    this.uploadedFile.set(null);
    this.uploadedFiles.set([]);
    this.errorMessage.set('');
    this.processingFiles.set(false);
    if (this.fileInput) {
      this.fileInput.nativeElement.value = '';
    }
  }

  // Private methods
  private handleFileSelection(file: File): void {
    this.logger.info('[FILE-UPLOAD-COMPONENT] Handle file selection', {
      name: file.name,
      size: file.size,
      type: file.type
    }, 'FileUploadComponent');
    const error = this.fileUploadService.validateFile(file);
    if (error) {
      this.logger.error('[FILE-UPLOAD-COMPONENT] Validation error', error, 'FileUploadComponent');
      this.errorMessage.set(error);
      return;
    }
    this.logger.debug('[FILE-UPLOAD-COMPONENT] File validation passed', undefined, 'FileUploadComponent');
    this.uploadedFile.set(file);
    this.errorMessage.set('');
    this.fileAnalyzed.set(false);

    this.logger.debug('[FILE-UPLOAD-COMPONENT] Emitting file uploaded event', undefined, 'FileUploadComponent');
    this.fileUploaded.emit(file);
    this.fileAnalyzed.set(true);

    setTimeout(() => {
      this.logger.debug('[FILE-UPLOAD-COMPONENT] Clearing upload after timeout', undefined, 'FileUploadComponent');
      this.onClear();
      this.fileAnalyzed.set(false);
    }, 500);
  }

  private handleMultipleFileSelection(files: File[]): void {
    this.logger.info('[FILE-UPLOAD-COMPONENT] Handle multiple file selection', { count: files.length }, 'FileUploadComponent');
    
    // Validate all files first
    const errors: string[] = [];
    const validFiles: File[] = [];
    
    for (const file of files) {
      const error = this.fileUploadService.validateFile(file);
      if (error) {
        errors.push(`${file.name}: ${error}`);
      } else {
        validFiles.push(file);
      }
    }
    
    if (errors.length > 0) {
      this.logger.error('[FILE-UPLOAD-COMPONENT] Validation errors', errors, 'FileUploadComponent');
      this.errorMessage.set(errors.join('\n'));
      if (validFiles.length === 0) {
        return;
      }
    }
    
    this.logger.info('[FILE-UPLOAD-COMPONENT] Valid files', { count: validFiles.length }, 'FileUploadComponent');
    this.uploadedFiles.set(validFiles);
    this.errorMessage.set('');
    this.processingFiles.set(true);
    
    this.logger.debug('[FILE-UPLOAD-COMPONENT] Emitting files uploaded event', undefined, 'FileUploadComponent');
    this.filesUploaded.emit(validFiles);
    
    setTimeout(() => {
      this.logger.debug('[FILE-UPLOAD-COMPONENT] Clearing upload after timeout', undefined, 'FileUploadComponent');
      this.onClear();
    }, 1000);
  }
}
