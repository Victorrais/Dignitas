/* Tela de login: valida os campos, envia o token CSRF e mostra o resultado da tentativa anterior. */
document.addEventListener('DOMContentLoaded', function () {
    const form = document.getElementById('form-login');
    const params = new URLSearchParams(location.search);
    document.getElementById('aviso-erro').hidden = !params.has('erro');
    document.getElementById('aviso-saiu').hidden = !params.has('saiu');

    // Tema escolhido no app vale também aqui
    try {
        const tema = localStorage.getItem('dignitas.tema');
        if (tema === 'dark' || tema === 'light') document.documentElement.dataset.theme = tema;
    } catch (e) { /* sem armazenamento: segue o sistema */ }

    function obrigatorio(id, mensagem) {
        const campo = document.getElementById(id);
        const vazio = campo.value.trim() === '';
        campo.closest('.field').classList.toggle('invalid', vazio);
        campo.setAttribute('aria-invalid', String(vazio));
        document.getElementById('erro-' + id).textContent = vazio ? mensagem : '';
        return !vazio;
    }

    form.addEventListener('submit', function (e) {
        const ok = [obrigatorio('usuario', 'Informe o usuário.'), obrigatorio('senha', 'Informe a senha.')].every(Boolean);
        if (!ok) {
            e.preventDefault();
            return;
        }
        const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        document.getElementById('csrf').value = m ? decodeURIComponent(m[1]) : '';
    });

    document.getElementById('usuario').focus();
});
