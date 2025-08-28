import { Component, OnInit, inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';

import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';

import { HeaderComponent } from '../core/components/header/header.component';
import { FileUploadComponent } from '../features/file-upload/components/file-upload.component';
import { SemanticTypesListComponent } from '../features/semantic-types/components/semantic-types-list.component';
import { AnalysesListComponent } from '../features/analyses/components/analyses-list.component';

import { FileUploadService } from '../core/services/file-upload.service';
import { LoggerService } from '../core/services/logger.service';
import { SemanticTypeService } from '../core/services/semantic-type.service';

@Component({
  selector: 'app-main',
  standalone: true,
  imports: [
    CommonModule,
    ConfirmDialogModule,
    HeaderComponent,
    FileUploadComponent,
    SemanticTypesListComponent,
    AnalysesListComponent,
  ],
  templateUrl: './main.component.html',
  styleUrl: './main.component.css',
  providers: [ConfirmationService],
})
export class MainComponent implements OnInit {
  errorMessage = '';

  private fileUploadService = inject(FileUploadService);
  private semanticTypeService = inject(SemanticTypeService);
  private platformId = inject(PLATFORM_ID);
  private logger = inject(LoggerService);

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.semanticTypeService.loadTypes();
    }
  }

  onFileUploaded(file: File): void {
    this.logger.info('[MAIN] File uploaded', {
      name: file.name,
      size: file.size,
      type: file.type,
      lastModified: file.lastModified
    }, 'MainComponent');
    this.errorMessage = '';

    this.fileUploadService.processFile(file).subscribe({
      next: () => {
        this.logger.info('[MAIN] File processed successfully', undefined, 'MainComponent');
      },
      error: (error) => {
        this.logger.error('[MAIN] File processing error', error, 'MainComponent');
        this.logger.error('[MAIN] Error details', {
          message: error?.message,
          status: error?.status,
          statusText: error?.statusText,
          url: error?.url,
          error: error?.error
        }, 'MainComponent');
        this.errorMessage = 'Failed to process file. Please try again.';
      },
    });
  }

  onMultipleFilesUploaded(files: File[]): void {
    this.logger.info('[MAIN] Multiple files uploaded', { count: files.length }, 'MainComponent');
    this.errorMessage = '';

    this.fileUploadService.processMultipleFiles(files).subscribe({
      next: (progress) => {
        this.logger.info('[MAIN] Processing progress', progress, 'MainComponent');
        if (progress.errors.length > 0) {
          this.errorMessage = `Processed ${progress.completed} of ${progress.total} files. Errors:\n${progress.errors.join('\n')}`;
        } else if (progress.completed === progress.total) {
          this.logger.info('[MAIN] All files processed successfully', undefined, 'MainComponent');
        }
      },
      error: (error) => {
        this.logger.error('[MAIN] Multiple file processing error', error, 'MainComponent');
        this.errorMessage = 'Failed to process files. Please try again.';
      },
      complete: () => {
        this.logger.info('[MAIN] Multiple file processing completed', undefined, 'MainComponent');
      }
    });
  }
}
