<#ftl output_format="HTML">
<#-- @ftlvariable name="data" type="io.qameta.allure.attachment.http.HttpRequestAttachment" -->
<#--
  Same as Allure's default http-request.ftl, with three differences:
  1. The value of the Authorization header is masked. The scheme stays visible ("Bearer [masked]",
     "Basic [masked]"); a value without a scheme becomes just "[masked]".
  2. The Curl section is left out on purpose: it repeats every header, including Authorization,
     and it repeats the whole body a second time.
  3. A long body is cut after maxBodyLength characters, with a note that says how long it really was.
     The oversized-payload scenario sends a body of about 12 million characters; showing all of it
     makes the browser run out of memory and the Allure page crashes (STATUS_BREAKPOINT).
-->
<#assign maxBodyLength = 5000>
<div><#if data.method??>${data.method}<#else>GET</#if> to <#if data.url??>${data.url}<#else>Unknown</#if></div>

<#if data.body??>
<h4>Body</h4>
<div>
    <pre class="preformated-text">
    <#if data.body?length gt maxBodyLength>
    <#t>${data.body?substring(0, maxBodyLength)}
... [body cut here: it has ${data.body?length?c} characters, only the first ${maxBodyLength?c} are shown]
    <#else>
    <#t>${data.body}
    </#if>
    </pre>
</div>
</#if>

<#if (data.headers)?has_content>
<h4>Headers</h4>
<div>
    <#list data.headers as name, value>
        <#if name?lower_case == "authorization">
        <#assign headerValue = value!"">
        <div>${name}: <#if headerValue?contains(" ")>${headerValue?keep_before(" ")} </#if>[masked]</div>
        <#else>
        <div>${name}: ${value!"null"}</div>
        </#if>
    </#list>
</div>
</#if>


<#if (data.cookies)?has_content>
<h4>Cookies</h4>
<div>
    <#list data.cookies as name, value>
        <div>${name}: ${value!"null"}</div>
    </#list>
</div>
</#if>
