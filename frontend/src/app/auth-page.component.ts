import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from './auth.service';

type AuthField = 'email' | 'password' | 'claimCode';

const passwordByteLimit: ValidatorFn = control =>
  new TextEncoder().encode(control.value).length > 72 ? { passwordBytes: true } : null;

@Component({
  selector: 'app-auth-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './auth-page.component.html',
  styleUrl: './auth-page.component.css',
})
export class AuthPageComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder).nonNullable;

  protected readonly registration = this.route.snapshot.data['registration'] === true;
  protected readonly form = this.formBuilder.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [
      Validators.required,
      Validators.pattern(/\S/u),
      passwordByteLimit,
      ...(this.registration ? [Validators.minLength(12)] : []),
    ]],
    claimCode: '',
  });
  private readonly email = toSignal(this.form.controls.email.valueChanges, { initialValue: '' });

  protected readonly showClaimCode = computed(() =>
    this.registration && this.email().trim().toLowerCase() === 'user01@mail.com',
  );
  protected readonly sessionNotice = this.auth.sessionNotice;
  protected readonly pending = signal(false);
  protected readonly failure = signal('');
  protected readonly serverErrors = signal<Partial<Record<AuthField, string[]>>>({});

  protected fieldError(field: AuthField): string | undefined {
    const serverError = this.serverErrors()[field]?.[0];
    const control = this.form.controls[field];
    if (serverError || !control.touched || !control.invalid) {
      return serverError;
    }
    if (field === 'email') {
      return control.hasError('required') ? 'L’adresse e-mail est obligatoire.' : 'Saisis une adresse e-mail valide.';
    }
    if (field === 'claimCode') {
      return 'Le code est limité à 128 caractères.';
    }
    if (control.hasError('required') || control.hasError('pattern')) {
      return 'Le mot de passe est obligatoire.';
    }
    return control.hasError('minlength')
      ? 'Le mot de passe doit contenir au moins 12 caractères.'
      : 'Le mot de passe est limité à 72 octets UTF-8.';
  }

  protected clearFieldError(field: AuthField) {
    this.serverErrors.update(errors => ({ ...errors, [field]: undefined }));
  }

  protected submit() {
    if (this.pending()) {
      return;
    }
    this.failure.set('');
    this.serverErrors.set({});
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { email, password, claimCode } = this.form.getRawValue();
    const credentials = { email, password };
    const request = this.registration
      ? this.auth.register({ ...credentials, claimCode: this.showClaimCode() ? claimCode : undefined })
      : this.auth.login(credentials);

    this.pending.set(true);
    request.pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.pending.set(false)),
    ).subscribe({
      next: () => {
        this.form.reset();
        void this.router.navigateByUrl('/', { replaceUrl: true });
      },
      error: (error: unknown) => this.showFailure(error),
    });
  }

  private showFailure(error: unknown) {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) {
        this.failure.set('Impossible de joindre l’API. Vérifie que Spring Boot est démarré.');
        return;
      }
      if (typeof error.error?.message === 'string') {
        this.failure.set(error.error.message);
        this.serverErrors.set(error.error.errors ?? {});
        return;
      }
      if (error.status === 401 || error.status === 403) {
        this.failure.set(error.status === 401 ? 'Adresse e-mail ou mot de passe incorrect.' : 'Accès refusé.');
        return;
      }
    }
    this.failure.set('L’authentification a échoué. Réessaie dans un instant.');
  }
}
