import {Injectable, signal} from '@angular/core';
import {DOCUMENT} from '@angular/common';
import {inject} from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class LanguageService {
  private document = inject(DOCUMENT);
  currentLanguage = signal('SK');

  switchLanguage() {
    const newLang = this.currentLanguage() === 'SK' ? 'EN' : 'SK';
    this.currentLanguage.set(newLang);

    const currentUrl = this.document.location.pathname;
    if (newLang === 'EN') {
      this.document.location.href = '/en' + currentUrl;
    } else {
      const skUrl = currentUrl.replace('/en', '');
      this.document.location.href = skUrl || '/';
    }
  }
}
