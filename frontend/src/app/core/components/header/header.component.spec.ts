import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MessageService } from 'primeng/api';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { HeaderComponent } from './header.component';
import { AwsBedrockService } from '../../services/aws-bedrock.service';
import { ConfigService } from '../../services/config.service';
import { AuthService } from '../../../auth/auth.service';
import { SemanticTypeService } from '../../services/semantic-type.service';
import { LoggerService } from '../../services/logger.service';
import { TableClassifierService } from '../../services/table-classifier.service';

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;
  let awsBedrockService: jasmine.SpyObj<AwsBedrockService>;
  let configService: jasmine.SpyObj<ConfigService>;
  let authService: jasmine.SpyObj<AuthService>;
  let semanticTypeService: jasmine.SpyObj<SemanticTypeService>;
  let loggerService: jasmine.SpyObj<LoggerService>;
  let dialogService: jasmine.SpyObj<DialogService>;
  let router: jasmine.SpyObj<Router>;

  const mockConfig = {
    baseZIndex: 10000,
    maxFileSize: 10485760,
    maxRows: 1000,
    apiUrl: '/api',
    httpTimeoutMs: 30000,
    httpRetryCount: 3,
    httpLongTimeoutMs: 60000,
    defaultHighThreshold: 95,
    defaultMediumThreshold: 80,
    defaultLowThreshold: 50,
    highThresholdMin: 90,
    highThresholdMax: 100,
    mediumThresholdMin: 70,
    mediumThresholdMax: 89,
    lowThresholdMin: 0,
    lowThresholdMax: 69,
    notificationDelayMs: 3000,
    reanalysisDelayMs: 1000,
  };

  beforeEach(async () => {
    const awsSpy = jasmine.createSpyObj('AwsBedrockService', ['getAwsStatus', 'clearAwsCredentials']);
    const configSpy = jasmine.createSpyObj('ConfigService', ['getConfig']);
    const dialogSpy = jasmine.createSpyObj('DialogService', ['open']);
    const authSpy = jasmine.createSpyObj('AuthService', ['logout']);
    const semanticSpy = jasmine.createSpyObj('SemanticTypeService', ['refreshTypes']);
    const loggerSpy = jasmine.createSpyObj('LoggerService', ['info', 'debug', 'error', 'warn']);
    const tableClassifierSpy = jasmine.createSpyObj('TableClassifierService', [
      'getAllAnalyses',
      'reanalyzeWithUpdatedTypes',
      'deleteAnalysis',
      'deleteAllAnalyses',
      'classifyTable',
      'analyzeTable',
    ]);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    const messageSpy = jasmine.createSpyObj('MessageService', ['add', 'clear']);

    configSpy.getConfig.and.returnValue(mockConfig);
    // Provide apiUrl getter used by services under test
    Object.defineProperty(configSpy, 'apiUrl', { get: () => mockConfig.apiUrl });
    awsSpy.getAwsStatus.and.returnValue(of({ configured: false, message: 'Not configured' }));
    semanticSpy.refreshTypes.and.returnValue(Promise.resolve());
    tableClassifierSpy.getAllAnalyses.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [HeaderComponent, NoopAnimationsModule, HttpClientTestingModule],
      providers: [
        { provide: AwsBedrockService, useValue: awsSpy },
        { provide: ConfigService, useValue: configSpy },
        { provide: DialogService, useValue: dialogSpy },
        { provide: AuthService, useValue: authSpy },
        { provide: SemanticTypeService, useValue: semanticSpy },
        { provide: LoggerService, useValue: loggerSpy },
        { provide: TableClassifierService, useValue: tableClassifierSpy },
        { provide: Router, useValue: routerSpy },
        { provide: MessageService, useValue: messageSpy },
      ],
    })
    .overrideComponent(HeaderComponent, {
      set: {
        providers: [
          { provide: DialogService, useValue: dialogSpy }
        ]
      }
    })
    .compileComponents();

    awsBedrockService = TestBed.inject(AwsBedrockService) as jasmine.SpyObj<AwsBedrockService>;
    configService = TestBed.inject(ConfigService) as jasmine.SpyObj<ConfigService>;
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    semanticTypeService = TestBed.inject(SemanticTypeService) as jasmine.SpyObj<SemanticTypeService>;
    loggerService = TestBed.inject(LoggerService) as jasmine.SpyObj<LoggerService>;
    TestBed.inject(TableClassifierService) as jasmine.SpyObj<TableClassifierService>;
    dialogService = TestBed.inject(DialogService) as jasmine.SpyObj<DialogService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('ngOnInit', () => {
    it('should check AWS status and update menu items on initialization', () => {
      spyOn(component as any, 'checkAwsStatus');
      spyOn(component as any, 'updateMenuItems');

      component.ngOnInit();

      expect((component as any)['checkAwsStatus']).toHaveBeenCalled();
      expect((component as any)['updateMenuItems']).toHaveBeenCalled();
    });
  });

  describe('ngOnDestroy', () => {
    it('should close dialog if open', () => {
      const mockDialogRef = jasmine.createSpyObj('DynamicDialogRef', ['close']);
      component['dialogRef'] = mockDialogRef;

      component.ngOnDestroy();

      expect(mockDialogRef.close).toHaveBeenCalled();
    });

    it('should do nothing if no dialog is open', () => {
      component['dialogRef'] = undefined;

      expect(() => component.ngOnDestroy()).not.toThrow();
    });
  });

  describe('openAwsConfigModal', () => {
    let mockDialogRef: jasmine.SpyObj<DynamicDialogRef>;

    beforeEach(() => {
      mockDialogRef = jasmine.createSpyObj('DynamicDialogRef', ['close']);
      mockDialogRef.onClose = of(null);
      dialogService.open.and.returnValue(mockDialogRef);
    });

    it('should open AWS configuration modal with correct config', () => {
      component.openAwsConfigModal();

      expect(dialogService.open).toHaveBeenCalledWith(
        jasmine.anything(),
        {
          header: 'Configure AWS Bedrock',
          width: '70%',
          style: { 'max-width': '800px' },
          contentStyle: { overflow: 'auto' },
          baseZIndex: mockConfig.baseZIndex,
          modal: true,
          dismissableMask: true,
          closeOnEscape: true,
          closable: true,
        }
      );
      expect(component['dialogRef']).toBe(mockDialogRef);
    });

    it('should handle dialog close without result', () => {
      mockDialogRef.onClose = of(null);
      spyOn(component as any, 'fetchModelInfo');
      spyOn(component as any, 'updateMenuItems');

      component.openAwsConfigModal();

      expect((component as any)['fetchModelInfo']).not.toHaveBeenCalled();
      expect((component as any)['updateMenuItems']).not.toHaveBeenCalled();
    });

    it('should handle dialog close with configuration result', async () => {
      const configResult = {
        configured: true,
        region: 'us-west-2',
        modelId: 'claude-3-sonnet'
      };
      mockDialogRef.onClose = of(configResult);
      spyOn(component as any, 'fetchModelInfo').and.returnValue(Promise.resolve());
      spyOn(component as any, 'updateMenuItems');

      component.openAwsConfigModal();

      // Wait for the async operation to complete
      await new Promise(resolve => setTimeout(resolve, 0));

      expect(component.awsConfigured()).toBe(true);
      expect((component as any)['currentRegion']()).toBe('us-west-2');
      expect((component as any)['currentModelId']()).toBe('claude-3-sonnet');
      expect((component as any)['fetchModelInfo']).toHaveBeenCalledWith('us-west-2', 'claude-3-sonnet');
      expect((component as any)['updateMenuItems']).toHaveBeenCalled();
    });

    it('should use default baseZIndex if config is not available', () => {
      configService.getConfig.and.returnValue(null as any);

      component.openAwsConfigModal();

      expect(dialogService.open).toHaveBeenCalledWith(
        jasmine.anything(),
        jasmine.objectContaining({
          baseZIndex: 10000
        })
      );
    });
  });

  describe('checkAwsStatus', () => {
    beforeEach(() => {
      spyOn(component as any, 'updateMenuItems');
      spyOn(component as any, 'fetchModelInfo').and.returnValue(Promise.resolve());
    });

    it('should set awsConfigured to true when AWS is configured', async () => {
      const configuredStatus = {
        configured: true,
        message: 'AWS configured',
        region: 'us-east-1',
        modelId: 'claude-v2'
      };
      awsBedrockService.getAwsStatus.and.returnValue(of(configuredStatus));

      await (component as any)['checkAwsStatus']();

      expect(component.awsConfigured()).toBe(true);
      expect((component as any)['currentRegion']()).toBe('us-east-1');
      expect((component as any)['currentModelId']()).toBe('claude-v2');
      expect((component as any)['fetchModelInfo']).toHaveBeenCalledWith('us-east-1', 'claude-v2');
      expect((component as any)['updateMenuItems']).toHaveBeenCalled();
    });

    it('should set awsConfigured to true but not fetch model info when region/modelId missing', async () => {
      const configuredStatus = {
        configured: true,
        message: 'AWS configured'
      };
      awsBedrockService.getAwsStatus.and.returnValue(of(configuredStatus));

      await (component as any)['checkAwsStatus']();

      expect(component.awsConfigured()).toBe(true);
      expect((component as any)['fetchModelInfo']).not.toHaveBeenCalled();
      expect((component as any)['updateMenuItems']).toHaveBeenCalled();
    });

    it('should handle unconfigured AWS status', async () => {
      awsBedrockService.getAwsStatus.and.returnValue(of({ configured: false, message: 'Not configured' }));

      await (component as any)['checkAwsStatus']();

      expect(component.awsConfigured()).toBe(false);
      expect((component as any)['updateMenuItems']).toHaveBeenCalled();
    });

    it('should handle AWS status check error', async () => {
      awsBedrockService.getAwsStatus.and.returnValue(throwError(() => new Error('Network error')));

      await (component as any)['checkAwsStatus']();

      expect(component.awsConfigured()).toBe(false);
      expect((component as any)['updateMenuItems']).toHaveBeenCalled();
    });
  });

  describe('clearCredentials', () => {
    beforeEach(() => {
      spyOn(component as any, 'updateMenuItems');
    });

    it('should clear AWS credentials successfully', async () => {
      awsBedrockService.clearAwsCredentials.and.returnValue(of({ success: true }));
      (component as any)['awsConfigured'].set(true);

      await (component as any)['clearCredentials']();

      expect(awsBedrockService.clearAwsCredentials).toHaveBeenCalled();
      expect(component.awsConfigured()).toBe(false);
      expect((component as any)['updateMenuItems']).toHaveBeenCalled();
      expect(semanticTypeService.refreshTypes).toHaveBeenCalled();
    });

    it('should handle clear credentials failure gracefully', async () => {
      awsBedrockService.clearAwsCredentials.and.returnValue(throwError(() => new Error('Clear failed')));
      (component as any)['awsConfigured'].set(true);

      await (component as any)['clearCredentials']();

      expect(component.awsConfigured()).toBe(false);
      expect((component as any)['updateMenuItems']).toHaveBeenCalled();
      expect(semanticTypeService.refreshTypes).toHaveBeenCalled();
    });

    it('should handle semantic type refresh failure', async () => {
      awsBedrockService.clearAwsCredentials.and.returnValue(of({ success: true }));
      semanticTypeService.refreshTypes.and.returnValue(Promise.reject(new Error('Refresh failed')));

      await (component as any)['clearCredentials']();

      expect(loggerService.warn).toHaveBeenCalledWith(
        'Failed to refresh semantic types after clearing AWS credentials',
        jasmine.any(Error),
        'HeaderComponent'
      );
    });
  });

  describe('logout', () => {
    it('should logout and navigate to login page', () => {
      (component as any)['logout']();

      expect(authService.logout).toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('updateMenuItems', () => {
    it('should create menu items for configured AWS with full details', () => {
      (component as any)['awsConfigured'].set(true);
      (component as any)['currentRegion'].set('us-east-1');
      (component as any)['currentModelId'].set('claude-v2');
      (component as any)['modelDisplayName'].set('Claude v2');

      (component as any)['updateMenuItems']();

      expect(component.profileMenuItems.length).toBeGreaterThan(0);
      expect(component.profileMenuItems.some(item => item.label === 'AWS Configured')).toBe(true);
      expect(component.profileMenuItems.some(item => item.label?.includes('Region: us-east-1'))).toBe(true);
      expect(component.profileMenuItems.some(item => item.label?.includes('Model: Claude v2'))).toBe(true);
      expect(component.profileMenuItems.some(item => item.label === 'Reconfigure AWS')).toBe(true);
      expect(component.profileMenuItems.some(item => item.label === 'Clear AWS Credentials')).toBe(true);
      expect(component.profileMenuItems.some(item => item.label === 'Logout')).toBe(true);
    });

    it('should create menu items for unconfigured AWS', () => {
      (component as any)['awsConfigured'].set(false);

      (component as any)['updateMenuItems']();

      expect(component.profileMenuItems.length).toBe(3); // Configure AWS, separator, Logout
      expect(component.profileMenuItems.some(item => item.label === 'Configure AWS')).toBe(true);
      expect(component.profileMenuItems.some(item => item.label === 'Logout')).toBe(true);
      expect(component.profileMenuItems.some(item => item.label === 'AWS Configured')).toBe(false);
    });

    it('should test menu item commands for configured AWS', () => {
      (component as any)['awsConfigured'].set(true);
      (component as any)['currentRegion'].set('us-east-1');
      (component as any)['currentModelId'].set('claude-v2');
      spyOn(component, 'openAwsConfigModal');
      spyOn(component as any, 'clearCredentials');

      (component as any)['updateMenuItems']();

      // Test Reconfigure AWS command
      const reconfigureItem = component.profileMenuItems.find(item => item.label === 'Reconfigure AWS');
      expect(reconfigureItem).toBeDefined();
      reconfigureItem!.command!({});
      expect(component.openAwsConfigModal).toHaveBeenCalled();

      // Test Clear AWS Credentials command
      const clearItem = component.profileMenuItems.find(item => item.label === 'Clear AWS Credentials');
      expect(clearItem).toBeDefined();
      clearItem!.command!({});
      expect((component as any)['clearCredentials']).toHaveBeenCalled();
    });
  });

  describe('fetchModelInfo', () => {
    it('should set model display name when region and modelId are provided', async () => {
      await (component as any)['fetchModelInfo']('us-east-1', 'claude-v2');

      expect((component as any)['modelDisplayName']()).toBe('claude-v2');
    });

    it('should use current region and modelId when not provided', async () => {
      (component as any)['currentRegion'].set('us-west-2');
      (component as any)['currentModelId'].set('claude-3');

      await (component as any)['fetchModelInfo']();

      expect((component as any)['modelDisplayName']()).toBe('claude-3');
    });

    it('should not set model display name when region is missing', async () => {
      (component as any)['currentRegion'].set('');
      (component as any)['currentModelId'].set('claude-v2');
      (component as any)['modelDisplayName'].set('previous-value');

      await (component as any)['fetchModelInfo']();

      expect((component as any)['modelDisplayName']()).toBe('previous-value'); // Should remain unchanged
    });

    it('should not set model display name when modelId is missing', async () => {
      (component as any)['currentRegion'].set('us-east-1');
      (component as any)['currentModelId'].set('');
      (component as any)['modelDisplayName'].set('previous-value');

      await (component as any)['fetchModelInfo']();

      expect((component as any)['modelDisplayName']()).toBe('previous-value'); // Should remain unchanged
    });
  });

  describe('config getter', () => {
    it('should return config from configService', () => {
      const config = (component as any)['config'];
      expect(config).toBe(mockConfig);
      expect(configService.getConfig).toHaveBeenCalled();
    });
  });
});