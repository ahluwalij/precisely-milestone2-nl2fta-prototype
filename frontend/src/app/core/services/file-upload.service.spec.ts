import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { FileUploadService } from './file-upload.service';
import { ConfigService } from './config.service';
import { FtaClassifierService } from './fta-classifier.service';
import { AnalysisService } from './analysis.service';

describe('FileUploadService', () => {
  let service: FileUploadService;
  let _configService: jasmine.SpyObj<ConfigService>;
  let ftaService: jasmine.SpyObj<FtaClassifierService>;
  let analysisService: jasmine.SpyObj<AnalysisService>;

  beforeEach(() => {
    const configSpy = jasmine.createSpyObj('ConfigService', [], {
      maxFileSize: 10485760, // 10MB
      maxRows: 1000,
    });
    const ftaSpy = jasmine.createSpyObj('FtaClassifierService', [
      'classifyTable',
    ]);
    const analysisSpy = jasmine.createSpyObj('AnalysisService', ['addAnalysis', 'processAnalysisResult']);
    
    // Configure processAnalysisResult to return a mock FileAnalysis
    analysisSpy.processAnalysisResult.and.returnValue({
      id: 'test-analysis-id',
      fileName: 'test.csv',
      uploadTime: new Date('2024-01-01T00:00:00Z'),
      lastAnalyzedAt: new Date('2024-01-01T00:00:00Z'),
      classificationResults: {},
      tableData: [],
      dynamicColumns: [],
      originalData: [],
      isExpanded: false
    });

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        FileUploadService,
        { provide: ConfigService, useValue: configSpy },
        { provide: FtaClassifierService, useValue: ftaSpy },
        { provide: AnalysisService, useValue: analysisSpy },
      ],
    });

    service = TestBed.inject(FileUploadService);
    _configService = TestBed.inject(ConfigService) as jasmine.SpyObj<ConfigService>;
    ftaService = TestBed.inject(FtaClassifierService) as jasmine.SpyObj<FtaClassifierService>;
    analysisService = TestBed.inject(AnalysisService) as jasmine.SpyObj<AnalysisService>;
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('file type validation', () => {
    it('should recognize CSV files by extension', () => {
      const csvFile = new File(['test,data'], 'test.csv', { type: 'text/csv' });
      expect(service.isCSVFile(csvFile)).toBe(true);
      expect(service.isSQLFile(csvFile)).toBe(false);
    });

    it('should recognize SQL files by extension', () => {
      const sqlFile = new File(['CREATE TABLE test'], 'dump.sql', { type: 'text/sql' });
      expect(service.isSQLFile(sqlFile)).toBe(true);
      expect(service.isCSVFile(sqlFile)).toBe(false);
    });

    it('should extract file extensions correctly', () => {
      expect(service.getFileExtension('test.csv')).toBe('csv');
      expect(service.getFileExtension('data.SQL')).toBe('SQL');
      expect(service.getFileExtension('complex.file.name.csv')).toBe('csv');
      expect(service.getFileExtension('file_without_extension')).toBe('');
    });
  });

  describe('file validation', () => {
    it('should validate files correctly', () => {
      const validFile = new File(['test,data'], 'test.csv', { type: 'text/csv' });
      const largeFile = new File(['x'.repeat(20000000)], 'large.csv', { type: 'text/csv' });
      const emptyFile = new File([''], 'empty.csv', { type: 'text/csv' });

      expect(service.validateFile(validFile)).toBeNull();
      expect(service.validateFile(largeFile)).toContain('exceeds');
      expect(service.validateFile(emptyFile)).toContain('empty');
    });

    it('should reject files with invalid names', () => {
      const invalidFile = new File(['test'], '../test.csv', { type: 'text/csv' });
      expect(service.validateFile(invalidFile)).toContain('Invalid file name');
    });

    it('should reject unsupported file types', () => {
      const txtFile = new File(['test'], 'test.txt', { type: 'text/plain' });
      expect(service.validateFile(txtFile)).toContain('supported file type');
    });
  });

  describe('supported file types', () => {
    it('should have correct supported file types configuration', () => {
      expect(service.supportedFileTypes).toBeDefined();
      expect(service.supportedFileTypes.length).toBeGreaterThan(0);

      const csvType = service.supportedFileTypes.find(t => t.extension === 'csv');
      const sqlType = service.supportedFileTypes.find(t => t.extension === 'sql');

      expect(csvType).toBeDefined();
      expect(sqlType).toBeDefined();
      expect(csvType!.mimeTypes).toContain('text/csv');
      expect(sqlType!.mimeTypes).toContain('application/sql');
    });
  });

  describe('CSV parsing', () => {
    it('should parse valid CSV content', (done) => {
      const csvContent = 'name,age,email\nJohn,30,john@example.com\nJane,25,jane@example.com';
      const csvFile = new File([csvContent], 'test.csv', { type: 'text/csv' });

      service.parseCSV(csvFile).subscribe({
        next: (result) => {
          expect(result.columns).toEqual(['name', 'age', 'email']);
          expect(result.data.length).toBe(2);
          expect(result.data[0]).toEqual({
            name: 'John',
            age: 30, // Papa Parse converts numbers automatically
            email: 'john@example.com'
          });
          expect(result.fileName).toBe('test.csv');
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });

    it('should handle CSV parsing errors', (done) => {
      const consoleSpy = spyOn(console, 'error');
      const invalidCsv = new File([''], 'empty.csv', { type: 'text/csv' });

      service.parseCSV(invalidCsv).subscribe({
        next: () => {
          fail('Should have failed for empty CSV');
        },
        error: (error) => {
          expect(error).toBeDefined();
          expect(consoleSpy).toHaveBeenCalled();
          done();
        }
      });
    });
  });

  describe('file processing', () => {
    it('should successfully process CSV files', (done) => {
      const csvContent = 'name,value\ntest,123';
      const csvFile = new File([csvContent], 'test.csv', { type: 'text/csv' });

      const mockResponse = {
        analysisId: 'test-id',
        tableName: 'test.csv',
        columnClassifications: {},
        data: [],
        processingMetadata: {
          totalColumns: 2,
          analyzedColumns: 2,
          totalRowsProcessed: 1,
          processingTimeMs: 100,
          ftaVersion: '1.0.0',
          localeUsed: 'en-US'
        }
      };

      ftaService.classifyTable.and.returnValue(of(mockResponse));

      service.processFile(csvFile).subscribe({
        next: () => {
          expect(ftaService.classifyTable).toHaveBeenCalled();
          expect(analysisService.addAnalysis).toHaveBeenCalled();
          done();
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });

    it('should handle file processing errors', (done) => {
      const consoleSpy = spyOn(console, 'error');
      const csvFile = new File([''], 'test.csv', { type: 'text/csv' }); // Empty file causes CSV parsing error
      
      service.processFile(csvFile).subscribe({
        next: () => {
          fail('Should have failed');
        },
        error: (error) => {
          expect(error.message).toBe('No data found in CSV file');
          expect(consoleSpy).toHaveBeenCalled();
          done();
        }
      });
    });
  });

  describe('multiple file processing', () => {
    it('should process multiple files and report progress', (done) => {
      const files = [
        new File(['name,value\ntest1,123'], 'test1.csv', { type: 'text/csv' }),
        new File(['name,value\ntest2,456'], 'test2.csv', { type: 'text/csv' })
      ];

      ftaService.classifyTable.and.returnValue(of({
        analysisId: 'test-id',
        tableName: 'test.csv',
        columnClassifications: {},
        data: [],
        processingMetadata: {
          totalColumns: 2,
          analyzedColumns: 2,
          totalRowsProcessed: 1,
          processingTimeMs: 100,
          ftaVersion: '1.0.0',
          localeUsed: 'en-US'
        }
      }));

      let doneCalled = false;
      service.processMultipleFiles(files).subscribe({
        next: (progress) => {
          expect(progress.total).toBe(2);
          if (progress.completed === 2 && !doneCalled) {
            doneCalled = true;
            expect(progress.errors.length).toBe(0);
            done();
          }
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });

    it('should handle errors in multiple file processing', (done) => {
      const consoleSpy = spyOn(console, 'error');
      const files = [
        new File(['name,value\ntest1,123'], 'test1.csv', { type: 'text/csv' }),
        new File([''], 'empty.csv', { type: 'text/csv' }) // This will fail
      ];

      ftaService.classifyTable.and.returnValue(of({
        analysisId: 'test-id',
        tableName: 'test.csv',
        columnClassifications: {},
        data: [],
        processingMetadata: {
          totalColumns: 2,
          analyzedColumns: 2,
          totalRowsProcessed: 1,
          processingTimeMs: 100,
          ftaVersion: '1.0.0',
          localeUsed: 'en-US'
        }
      }));

      let doneCalled = false;
      service.processMultipleFiles(files).subscribe({
        next: (progress) => {
          if (progress.completed === 2 && !doneCalled) {
            doneCalled = true;
            expect(progress.errors.length).toBe(1);
            expect(consoleSpy).toHaveBeenCalled();
            done();
          }
        },
        error: (error) => {
          fail('Should not have failed: ' + error);
        }
      });
    });
  });
});