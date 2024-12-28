<h1>📍 Aplicativo Geofence - Registro Automático de Funcionários</h1>
<p>
  Este aplicativo foi desenvolvido em <b>Java</b> com o objetivo de registrar automaticamente funcionários ao chegarem em um local específico.  
  Ele utiliza geofencing para rastrear a localização, monitorar o tempo de permanência no local e integrar esses dados ao sistema de controle de funcionários desenvolvido em Flutter.
</p>
<p>
  O aplicativo interage diretamente com o projeto Flutter <a href="https://github.com/CustomClothesTcc/Analise-de-Ponto" target="_blank">Análise de Ponto</a>, permitindo o gerenciamento centralizado de informações de funcionários.
</p>

---

<h2>🏗️ Arquitetura do Projeto</h2>
<p>
  A estrutura do projeto segue as convenções padrão para projetos Android em Java, com os seguintes diretórios principais:
</p>
<pre>
.idea/                   # Configurações do projeto no IntelliJ IDEA.
app/                     # Código-fonte principal do aplicativo.
gradle/                  # Configurações e scripts de build do Gradle.
.gitignore               # Arquivo para exclusões de controle de versão.
Acesso Inteligente.zip   # Arquivo contendo materiais adicionais ou builds do projeto.
</pre>

---

<h2>📋 Funcionalidades</h2>
<ul>
  <li><b>Registro Automático:</b> Funcionários são registrados automaticamente ao entrar em uma área geográfica predefinida.</li>
  <li><b>Monitoramento de Permanência:</b> Tempo de permanência no local é calculado e registrado.</li>
  <li><b>Sincronização de Dados:</b> Informações coletadas são enviadas para o sistema em Flutter para análise e gestão.</li>
</ul>

---

<h2>🚀 Como Usar</h2>
<ol>
  <li>Clone este repositório:</li>
  <pre>
git clone https://github.com/CustomClothesTcc/Aplicativo-Geofence.git
  </pre>
  <li>Abra o projeto no Android Studio ou IntelliJ IDEA.</li>
  <li>Certifique-se de que o ambiente de desenvolvimento para Android esteja configurado.</li>
  <li>Execute o projeto em um dispositivo físico ou emulador configurado com permissões de localização.</li>
  <li>Configure as áreas de geofencing no aplicativo e teste o registro automático ao entrar nas áreas definidas.</li>
</ol>

---

<h2>🛠️ Tecnologias Utilizadas</h2>
<ul>
  <li><b>Java:</b> Linguagem principal para desenvolvimento do aplicativo.</li>
  <li><b>Android SDK:</b> Ferramentas para desenvolvimento e execução de aplicativos Android.</li>
  <li><b>Geofencing API:</b> Para rastreamento de localização e detecção de entrada/saída em áreas geográficas.</li>
  <li><b>Gradle:</b> Gerenciador de builds do projeto.</li>
</ul>

---

<h2>📑 Documentação</h2>
<p>
  O arquivo <code>Acesso Inteligente.zip</code> contém materiais adicionais para o projeto, incluindo:
</p>
<ul>
  <li>Builds do aplicativo para testes.</li>
  <li>Manuais de configuração e uso.</li>
  <li>Especificações técnicas sobre o geofencing e integração.</li>
</ul>

---
