import { Component, signal, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { MenuModule } from 'primeng/menu';
import { ToastModule } from 'primeng/toast';
import { MenuItem } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { firstValueFrom } from 'rxjs';
import { AwsCredentialsModalComponent } from '../../../features/aws-credentials/components/aws-credentials-modal.component';
import { AwsBedrockService } from '../../services/aws-bedrock.service';
import { ConfigService } from '../../services/config.service';
import { AuthService } from '../../../auth/auth.service';
import { SemanticTypeService } from '../../services/semantic-type.service';
import { AnalysisService } from '../../services/analysis.service';
import { Router } from '@angular/router';
import { LoggerService } from '../../services/logger.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, ButtonModule, MenuModule, ToastModule],
  templateUrl: './header.component.html',
  styleUrl: './header.component.css',
  providers: [DialogService],
})
export class HeaderComponent implements OnInit, OnDestroy {
  private awsBedrockService = inject(AwsBedrockService);
  private configService = inject(ConfigService);
  private dialogService = inject(DialogService);
  private authService = inject(AuthService);
  private semanticTypeService = inject(SemanticTypeService);
  private router = inject(Router);
  private analysisService = inject(AnalysisService);
  private logger = inject(LoggerService);

  awsConfigured = signal(false);
  profileMenuItems: MenuItem[] = [];
  private dialogRef: DynamicDialogRef | undefined;
  private modelDisplayName = signal<string>('');
  private currentRegion = signal<string>('');
  private currentModelId = signal<string>('');

  private get config() {
    return this.configService.getConfig();
  }

  // Lifecycle hooks
  ngOnInit() {
    this.checkAwsStatus();
    this.updateMenuItems();
  }

  ngOnDestroy() {
    if (this.dialogRef) {
      this.dialogRef.close();
    }
  }

  // Public methods
  openAwsConfigModal(): void {
    this.dialogRef = this.dialogService.open(AwsCredentialsModalComponent, {
      header: 'Configure AWS Bedrock',
      width: '70%',
      style: { 'max-width': '800px' },
      contentStyle: { overflow: 'auto' },
      baseZIndex: this.config?.baseZIndex || 10000,
      modal: true,
      dismissableMask: true,
      closeOnEscape: true,
      closable: true,
    });

    this.dialogRef.onClose.subscribe(
      async (result: { configured?: boolean; region?: string; modelId?: string } | null) => {
        if (result?.configured) {
          this.awsConfigured.set(true);
          // Store the configuration from the dialog result
          if (result.region) this.currentRegion.set(result.region);
          if (result.modelId) this.currentModelId.set(result.modelId);
          await this.fetchModelInfo(result.region, result.modelId);
          // Force-refresh semantic types so the list shows the updated count immediately
          try {
            // Use retry to ensure the UI reflects the new total without a manual reload
            await this.semanticTypeService.refreshTypesWithRetry();
          } catch {}
          this.updateMenuItems();
        }
      }
    );
  }

  // Private methods
  private async checkAwsStatus() {
    try {
      const status = await firstValueFrom(this.awsBedrockService.getAwsStatus());
      this.awsConfigured.set(status?.configured || false);

      // If configured, store the current configuration and fetch model information
      if (status?.configured && status.region && status.modelId) {
        this.currentRegion.set(status.region);
        this.currentModelId.set(status.modelId);
        await this.fetchModelInfo(status.region, status.modelId);
      }

      this.updateMenuItems();
    } catch {
      this.awsConfigured.set(false);
      this.updateMenuItems();
    }
  }

  private async fetchModelInfo(region?: string, modelId?: string) {
    const targetRegion = region || this.currentRegion();
    const targetModelId = modelId || this.currentModelId();

    if (targetRegion && targetModelId) {
      this.modelDisplayName.set(targetModelId);
    }
  }

  private updateMenuItems() {
    const region = this.currentRegion();
    const modelId = this.currentModelId();

    if (this.awsConfigured()) {
      const configInfo: MenuItem[] = [];

      if (region) {
        configInfo.push({
          label: `Region: ${region}`,
          icon: 'pi pi-globe',
          disabled: true,
          styleClass: 'configured-item',
        });
      }

      if (modelId) {
        // Use the actual model name from the backend
        const modelDisplay = this.modelDisplayName() || modelId;
        configInfo.push({
          label: `Model: ${modelDisplay}`,
          icon: 'pi pi-microchip-ai',
          disabled: true,
          styleClass: 'configured-item',
        });
      }

      this.profileMenuItems = [
        {
          label: 'AWS Configured',
          icon: 'pi pi-check-circle',
          disabled: true,
          styleClass: 'configured-item-header',
        },
        ...configInfo,
        {
          separator: true,
        },
        {
          label: 'Reconfigure AWS',
          icon: 'pi pi-cog',
          command: () => {
            this.openAwsConfigModal();
          },
        },
        {
          label: 'Clear AWS Credentials',
          icon: 'pi pi-sign-out',
          command: () => {
            this.clearCredentials();
          },
        },
        {
          separator: true,
        },
        {
          label: 'Logout',
          icon: 'pi pi-power-off',
          command: () => {
            this.logout();
          },
        },
      ];
    } else {
      this.profileMenuItems = [
        {
          label: 'Configure AWS',
          icon: 'pi pi-key',
          command: () => {
            this.openAwsConfigModal();
          },
        },
        {
          separator: true,
        },
        {
          label: 'Logout',
          icon: 'pi pi-power-off',
          command: () => {
            this.logout();
          },
        },
      ];
    }
  }

  private async clearCredentials() {
    try {
      await firstValueFrom(this.awsBedrockService.clearAwsCredentials());
      this.awsConfigured.set(false);
      this.updateMenuItems();
      
      // Refresh semantic types after clearing AWS credentials
      try {
        await this.semanticTypeService.refreshTypes();
      } catch (error) {
        this.logger.warn('Failed to refresh semantic types after clearing AWS credentials', error as unknown, 'HeaderComponent');
      }

      // Trigger reanalysis of all stored analyses so UI reflects fallback to built-ins
      try {
        await this.analysisService.reanalyzeAllAnalyses();
      } catch (err) {
        this.logger.warn('Failed to trigger reanalysis after clearing AWS credentials', err as unknown, 'HeaderComponent');
      }
    } catch {
      // Even if backend fails, update UI
      this.awsConfigured.set(false);
      this.updateMenuItems();
      
      // Still try to refresh semantic types
      try {
        await this.semanticTypeService.refreshTypes();
      } catch (error) {
        this.logger.warn('Failed to refresh semantic types after clearing AWS credentials', error as unknown, 'HeaderComponent');
      }

      // Attempt reanalysis anyway
      try {
        await this.analysisService.reanalyzeAllAnalyses();
      } catch (err) {
        this.logger.warn('Failed to trigger reanalysis after clearing AWS credentials', err as unknown, 'HeaderComponent');
      }
    }
  }

  // Removed dynamic import approach; rely on injected AnalysisService

  private logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
