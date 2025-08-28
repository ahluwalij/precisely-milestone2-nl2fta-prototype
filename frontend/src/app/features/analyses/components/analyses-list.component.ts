import {
  Component,
  inject,
  signal,
  effect,
  ChangeDetectionStrategy,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { AccordionModule } from 'primeng/accordion';
import { ButtonModule } from 'primeng/button';
import { AnalysisResultComponent } from './analysis-result/analysis-result.component';
import { AnalysisService } from '../../../core/services/analysis.service';
import { FileAnalysis } from '../../../shared/models/file-analysis.model';

@Component({
  selector: 'app-analyses-list',
  standalone: true,
  imports: [CommonModule, AccordionModule, ButtonModule, AnalysisResultComponent],
  templateUrl: './analyses-list.component.html',
  styleUrl: './analyses-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalysesListComponent {
  analysisService = inject(AnalysisService);

  // Stable state for accordion
  activeIndex = signal<number[]>([]);
  private userInteracting = false;
  private previousAnalysisIds: string[] = [];

  // Lazy loading properties
  private currentWindowSize = signal<number>(5); // Start with 5 analyses
  private loadedAnalysisIds = signal<Set<string>>(new Set()); // Track which analyses have been loaded

  // Computed properties for lazy loading
  windowSize = computed(() => this.currentWindowSize());

  visibleAnalyses = computed(() => {
    const analyses = this.analysisService.analyses();
    const windowSize = this.currentWindowSize();
    return analyses.slice(0, windowSize);
  });

  hasMoreAnalyses = computed(() => {
    const totalAnalyses = this.analysisService.analyses().length;
    const windowSize = this.currentWindowSize();
    return totalAnalyses > windowSize;
  });

  constructor() {
    // Update activeIndex when analyses change (but not during user interaction)
    effect(
      () => {
        const analyses = this.analysisService.analyses();
        if (!this.userInteracting) {
          const currentAnalysisIds = analyses.map(a => a.id);

          // Find newly added analyses
          const newAnalysisIds = currentAnalysisIds.filter(
            id => !this.previousAnalysisIds.includes(id)
          );

          if (newAnalysisIds.length > 0) {
            // Preserve existing expanded state and add new indices for new analyses
            const currentActiveIndices = this.activeIndex();
            const newIndices: number[] = [];

            // Find indices of newly added analyses within visible window
            const visibleAnalyses = this.visibleAnalyses();
            newAnalysisIds.forEach(newId => {
              const index = visibleAnalyses.findIndex((a: FileAnalysis) => a.id === newId);
              if (index !== -1) {
                newIndices.push(index);
                // Auto-load newly added analyses
                this.markAnalysisAsLoaded(newId);
              }
            });

            // Merge existing active indices with new ones
            const mergedIndices = [...new Set([...currentActiveIndices, ...newIndices])];
            this.activeIndex.set(mergedIndices);
          } else if (analyses.length === 0) {
            // Clear active indices when all analyses are removed
            this.activeIndex.set([]);
            this.loadedAnalysisIds.set(new Set());
          } else if (currentAnalysisIds.length < this.previousAnalysisIds.length) {
            // Handle removals - adjust indices for remaining expanded analyses
            const removedIds = this.previousAnalysisIds.filter(
              id => !currentAnalysisIds.includes(id)
            );
            const previousAnalyses = this.previousAnalysisIds;
            const currentActiveIndices = this.activeIndex();

            // Remove loaded state for deleted analyses
            const currentLoadedIds = this.loadedAnalysisIds();
            removedIds.forEach(removedId => {
              currentLoadedIds.delete(removedId);
            });
            this.loadedAnalysisIds.set(new Set(currentLoadedIds));

            // Map old indices to new indices for expanded analyses that still exist
            const newActiveIndices: number[] = [];
            const visibleAnalyses = this.visibleAnalyses();
            currentActiveIndices.forEach(oldIndex => {
              if (oldIndex < previousAnalyses.length) {
                const analysisId = this.previousAnalysisIds[oldIndex];
                if (!removedIds.includes(analysisId)) {
                  const newIndex = visibleAnalyses.findIndex(
                    (a: FileAnalysis) => a.id === analysisId
                  );
                  if (newIndex !== -1) {
                    newActiveIndices.push(newIndex);
                  }
                }
              }
            });

            this.activeIndex.set(newActiveIndices);
          }

          // Update previous analysis IDs
          this.previousAnalysisIds = currentAnalysisIds;
        }
      },
      { allowSignalWrites: true }
    );
  }

  onTabOpen(event: { index: number }): void {
    this.userInteracting = true;
    const current = this.activeIndex();
    if (!current.includes(event.index)) {
      this.activeIndex.set([...current, event.index]);
    }

    // Load the analysis content when accordion is opened
    const visibleAnalyses = this.visibleAnalyses();
    if (event.index < visibleAnalyses.length) {
      const analysisId = visibleAnalyses[event.index].id;
      this.markAnalysisAsLoaded(analysisId);
    }

    // Reset flag after interaction
    setTimeout(() => {
      this.userInteracting = false;
    }, 100);
  }

  onTabClose(event: { index: number }): void {
    this.userInteracting = true;
    const current = this.activeIndex();
    this.activeIndex.set(current.filter((i: number) => i !== event.index));
    // Reset flag after interaction
    setTimeout(() => {
      this.userInteracting = false;
    }, 100);
  }

  // Lazy loading methods
  isAnalysisLoaded(analysisId: string): boolean {
    return this.loadedAnalysisIds().has(analysisId);
  }

  markAnalysisAsLoaded(analysisId: string): void {
    const currentLoadedIds = this.loadedAnalysisIds();
    if (!currentLoadedIds.has(analysisId)) {
      const newLoadedIds = new Set(currentLoadedIds);
      newLoadedIds.add(analysisId);
      this.loadedAnalysisIds.set(newLoadedIds);
    }
  }

  loadMoreAnalyses(): void {
    const currentWindow = this.currentWindowSize();
    const totalAnalyses = this.analysisService.analyses().length;
    const increment = 5; // Load 5 more at a time
    const newWindowSize = Math.min(currentWindow + increment, totalAnalyses);
    this.currentWindowSize.set(newWindowSize);
  }

  removeAnalysis(analysisId: string): void {
    // Remove from loaded state
    const currentLoadedIds = this.loadedAnalysisIds();
    if (currentLoadedIds.has(analysisId)) {
      const newLoadedIds = new Set(currentLoadedIds);
      newLoadedIds.delete(analysisId);
      this.loadedAnalysisIds.set(newLoadedIds);
    }

    this.analysisService.removeAnalysis(analysisId);
  }

  clearAllAnalyses(): void {
    this.analysisService.clearAllAnalyses();
    this.loadedAnalysisIds.set(new Set());
    this.currentWindowSize.set(5); // Reset window size
  }

  // Track by function to prevent unnecessary re-renders
  trackByAnalysisId(index: number, analysis: FileAnalysis): string {
    return analysis.id;
  }
}
