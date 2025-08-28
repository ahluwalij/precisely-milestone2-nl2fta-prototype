import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DynamicDialogRef, DynamicDialogConfig } from 'primeng/dynamicdialog';
import { TextareaModule } from 'primeng/textarea';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-feedback-popover',
  standalone: true,
  imports: [CommonModule, FormsModule, TextareaModule, ButtonModule],
  template: `
    <div class="feedback-popover">
      <p class="feedback-prompt">
        {{
          data.feedbackType === 'positive'
            ? 'What did you like about the generated semantic type? (optional)'
            : 'How can we improve the semantic type generation? (optional)'
        }}
      </p>
      <textarea
        pTextarea
        [(ngModel)]="feedbackText"
        [placeholder]="getPlaceholder()"
        rows="5"
        class="w-full"
        [autoResize]="true"
      ></textarea>
      <div class="feedback-actions">
        <button
          pButton
          type="button"
          label="Cancel"
          class="p-button-text"
          (click)="cancel()"
        ></button>
        <button
          pButton
          type="button"
          label="Submit Feedback"
          class="p-button-primary"
          (click)="submit()"
        ></button>
      </div>
    </div>
  `,
  styles: [
    `
      .feedback-popover {
        padding: var(--space-2);
      }

      .feedback-prompt {
        margin-bottom: var(--space-4);
        color: var(--text-secondary);
      }

      .feedback-popover textarea {
        width: 100%;
        min-width: 100%;
        box-sizing: border-box;
      }

      .feedback-actions {
        display: flex;
        justify-content: flex-end;
        gap: 0.75rem;
        margin-top: 1rem;
      }
    `,
  ],
})
export class FeedbackPopoverComponent {
  private ref = inject(DynamicDialogRef);
  data = inject(DynamicDialogConfig).data;

  feedbackText = '';

  getPlaceholder(): string {
    if (this.data.feedbackType === 'positive') {
      return 'e.g., The examples were accurate, the pattern matched well...';
    } else {
      return 'e.g., The pattern was too broad, missing some examples...';
    }
  }

  cancel(): void {
    // Close without sending feedback
    this.ref.close();
  }

  submit(): void {
    // Send feedback with comment (even if empty)
    this.ref.close({ feedbackText: this.feedbackText.trim(), skipped: false });
  }
}