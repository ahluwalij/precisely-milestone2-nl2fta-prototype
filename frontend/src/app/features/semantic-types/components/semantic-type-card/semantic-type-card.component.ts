import { Component, Input, inject, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';
import {
  CustomSemanticType,
  LocaleConfig,
  FtaClassifierService,
} from '../../../../core/services/fta-classifier.service';
import { SemanticTypeService } from '../../../../core/services/semantic-type.service';

@Component({
  selector: 'app-semantic-type-card',
  standalone: true,
  imports: [CommonModule, ButtonModule, TooltipModule],
  templateUrl: './semantic-type-card.component.html',
  styleUrl: './semantic-type-card.component.css',
})
export class SemanticTypeCardComponent {
  @Input({ required: true }) type!: CustomSemanticType;
  @Output() deleted = new EventEmitter<string>();

  semanticTypeService = inject(SemanticTypeService);
  confirmationService = inject(ConfirmationService);
  ftaService = inject(FtaClassifierService);

  toggleExpanded(): void {
    this.semanticTypeService.toggleTypeExpanded(this.type.semanticType);
  }

  get isExpanded(): boolean {
    return this.semanticTypeService.isTypeExpanded(this.type.semanticType);
  }

  get isCustom(): boolean {
    return this.semanticTypeService.isCustomType(this.type.semanticType);
  }

  get category(): string {
    return this.semanticTypeService.getTypeCategory(this.type);
  }

  get filteredValidLocales(): LocaleConfig[] {
    if (!this.type.validLocales) return [];

    return this.type.validLocales.filter((locale: LocaleConfig) => {
      if (!locale.localeTag) return false;

      // Include universal locales
      if (locale.localeTag === '*') return true;

      // Include English locales
      // Check if the locale tag contains 'en' as a language code
      const tags = locale.localeTag.split(',');
      return tags.some((tag: string) => {
        // Check if it's an English locale (en, en-US, en-GB, etc.)
        return (
          tag.trim().toLowerCase() === 'en' ||
          tag.trim().toLowerCase().startsWith('en-') ||
          tag.trim().toLowerCase().startsWith('en_')
        );
      });
    });
  }

  deleteType(event: Event): void {
    event.stopPropagation(); // Prevent card expansion

    this.confirmationService.confirm({
      message: `Are you sure you want to delete the custom semantic type "${this.type.semanticType}"?`,
      header: 'Delete Confirmation',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.deleted.emit(this.type.semanticType);
      },
    });
  }
}
